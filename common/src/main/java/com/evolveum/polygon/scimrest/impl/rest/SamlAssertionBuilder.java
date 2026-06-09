/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.rest;

import com.evolveum.polygon.common.GuardedStringAccessor;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Builds a signed SAML 2.0 Bearer assertion for use with RFC 7522 OAuth 2.0 token requests.
 */
class SamlAssertionBuilder {

    private static final String SAML2_NS = "urn:oasis:names:tc:SAML:2.0:assertion";
    private static final String RSA_SHA256_URI = "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256";
    private static final long VALIDITY_SECONDS = 300;

    static String build(String issuer, String recipient, GuardedString privateKey) {
        try {
            var accessor = new GuardedStringAccessor();
            privateKey.access(accessor);
            PrivateKey key = parsePrivateKey(accessor.getClearString());

            String id = "_" + UUID.randomUUID().toString().replace("-", "");
            Instant now = Instant.now();
            Instant exp = now.plusSeconds(VALIDITY_SECONDS);

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            Document doc = dbf.newDocumentBuilder().newDocument();

            Element assertion = doc.createElementNS(SAML2_NS, "saml:Assertion");
            assertion.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:saml", SAML2_NS);
            assertion.setAttribute("Version", "2.0");
            assertion.setAttribute("ID", id);
            assertion.setIdAttribute("ID", true);
            assertion.setAttribute("IssueInstant", now.toString());
            doc.appendChild(assertion);

            Element issuerEl = doc.createElementNS(SAML2_NS, "saml:Issuer");
            issuerEl.setTextContent(issuer);
            assertion.appendChild(issuerEl);

            Element subject = doc.createElementNS(SAML2_NS, "saml:Subject");
            Element sc = doc.createElementNS(SAML2_NS, "saml:SubjectConfirmation");
            sc.setAttribute("Method", "urn:oasis:names:tc:SAML:2.0:cm:bearer");
            Element scd = doc.createElementNS(SAML2_NS, "saml:SubjectConfirmationData");
            scd.setAttribute("NotOnOrAfter", exp.toString());
            scd.setAttribute("Recipient", recipient);
            sc.appendChild(scd);
            subject.appendChild(sc);
            assertion.appendChild(subject);

            Element conditions = doc.createElementNS(SAML2_NS, "saml:Conditions");
            conditions.setAttribute("NotBefore", now.toString());
            conditions.setAttribute("NotOnOrAfter", exp.toString());
            assertion.appendChild(conditions);

            signAssertion(doc, assertion, id, key, issuerEl.getNextSibling());

            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter sw = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(sw));

            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(sw.toString().getBytes(StandardCharsets.UTF_8));
        } catch (ConnectorException e) {
            throw e;
        } catch (Exception e) {
            throw new ConnectorException("Failed to build SAML assertion: " + e.getMessage(), e);
        }
    }

    private static void signAssertion(Document doc, Element assertion, String id,
            PrivateKey key, org.w3c.dom.Node insertBefore) throws Exception {
        XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");

        List<Transform> transforms = List.of(
                fac.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null),
                fac.newTransform(CanonicalizationMethod.INCLUSIVE, (C14NMethodParameterSpec) null));

        Reference ref = fac.newReference(
                "#" + id,
                fac.newDigestMethod(DigestMethod.SHA256, null),
                transforms, null, null);

        SignedInfo si = fac.newSignedInfo(
                fac.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE, (C14NMethodParameterSpec) null),
                fac.newSignatureMethod(RSA_SHA256_URI, null),
                List.of(ref));

        XMLSignature sig = fac.newXMLSignature(si, null);
        DOMSignContext dsc = new DOMSignContext(key, assertion, insertBefore);
        sig.sign(dsc);
    }

    private static PrivateKey parsePrivateKey(String pem) {
        String stripped = pem
                .replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s", "");
        try {
            byte[] der = Base64.getDecoder().decode(stripped);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new ConnectorException("Failed to parse RSA private key for SAML assertion: " + e.getMessage(), e);
        }
    }
}
