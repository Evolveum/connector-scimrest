/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.auth.method.oauth2.saml;

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.ConnectorIOException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.*;

public class OAuth2SamlTests extends AbstractOAuth2SamlTests {

    private static final String TOKEN_ENDPOINT = "/oauth2/token";
    private static final String API_ENDPOINT = "/api/resource";
    private static final String CLIENT_ID = "test-client";

    private KeyPair rsaKeyPair;

    @BeforeMethod
    public void setUp() throws Exception {
        setUpWireMock();
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        rsaKeyPair = gen.generateKeyPair();
    }

    @AfterMethod
    public void tearDown() {
        tearDownWireMock();
    }

    @Test
    public void testSamlBearerFlow() {
        stubTokenEndpointWithStatus(TOKEN_ENDPOINT, 200, "saml-access-token");
        stubRestApiEndpoint(API_ENDPOINT, "saml-access-token");

        createConnector(rsaKeyPair.getPrivate()).test();

        wireMockServer.verify(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))
                .withRequestBody(containing("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Asaml2-bearer"))
                .withRequestBody(containing("client_id=" + CLIENT_ID))
                .withRequestBody(containing("assertion=")));

        wireMockServer.verify(getRequestedFor(urlEqualTo(API_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer saml-access-token")));

        String assertionXml = captureAssertion(TOKEN_ENDPOINT);
        assertTrue(assertionXml.contains("saml:Assertion"), "Assertion must contain saml:Assertion element");
        assertTrue(assertionXml.contains("saml:Issuer"), "Assertion must contain saml:Issuer element");
        assertTrue(assertionXml.contains(CLIENT_ID), "Assertion issuer must default to client_id");
        assertTrue(assertionXml.contains("Signature"), "Assertion must be signed");
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 2);
    }

    @Test
    public void testTokenReusedOnSubsequentCalls() {
        stubTokenEndpointWithStatus(TOKEN_ENDPOINT, 200, "reused-saml-token");
        stubRestApiEndpoint(API_ENDPOINT, "reused-saml-token");

        var connector = createConnector(rsaKeyPair.getPrivate());
        connector.test();
        connector.test();

        assertEquals(wireMockServer.findAll(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 3);
    }

    @Test
    public void testCustomIssuerInAssertion() {
        stubTokenEndpointWithStatus(TOKEN_ENDPOINT, 200, "saml-issuer-token");
        stubRestApiEndpoint(API_ENDPOINT, "saml-issuer-token");

        var config = configBuilder(rsaKeyPair.getPrivate());
        config.setIssuer("custom-issuer@example.com");
        createConnector(config).test();

        String assertionXml = captureAssertion(TOKEN_ENDPOINT);
        assertTrue(assertionXml.contains("custom-issuer@example.com"), "Custom issuer must appear in assertion");
    }

    @Test
    public void testIssuerDefaultsToClientIdWhenBlank() {
        stubTokenEndpointWithStatus(TOKEN_ENDPOINT, 200, "saml-default-issuer-token");
        stubRestApiEndpoint(API_ENDPOINT, "saml-default-issuer-token");

        var config = configBuilder(rsaKeyPair.getPrivate());
        config.setIssuer("   ");
        createConnector(config).test();

        String assertionXml = captureAssertion(TOKEN_ENDPOINT);
        assertTrue(assertionXml.contains(CLIENT_ID), "Issuer must default to client_id when blank");
    }

    @Test
    public void testScopeIsSentInTokenRequest() {
        stubTokenEndpointWithStatus(TOKEN_ENDPOINT, 200, "saml-scoped-token");
        stubRestApiEndpoint(API_ENDPOINT, "saml-scoped-token");

        var config = configBuilder(rsaKeyPair.getPrivate());
        config.setScope("openid profile");
        createConnector(config).test();

        wireMockServer.verify(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))
                .withRequestBody(containing("scope=openid+profile")));
    }

    @Test
    public void testClientIdOmittedWhenSchemeBasic() {
        stubTokenEndpointWithStatus(TOKEN_ENDPOINT, 200, "saml-basic-token");
        stubRestApiEndpoint(API_ENDPOINT, "saml-basic-token");

        var config = configBuilder(rsaKeyPair.getPrivate());
        config.setClientAuthenticationScheme("basic");
        createConnector(config).test();

        assertEquals(wireMockServer.findAll(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))
                .withRequestBody(containing("client_id="))).size(), 0);
    }

    @Test
    public void testSubjectConfirmationContainsTokenEndpoint() {
        stubTokenEndpointWithStatus(TOKEN_ENDPOINT, 200, "saml-recipient-token");
        stubRestApiEndpoint(API_ENDPOINT, "saml-recipient-token");

        createConnector(rsaKeyPair.getPrivate()).test();

        String assertionXml = captureAssertion(TOKEN_ENDPOINT);
        String tokenUrl = "http://localhost:" + wireMockServer.port() + TOKEN_ENDPOINT;
        assertTrue(assertionXml.contains(tokenUrl), "SubjectConfirmationData Recipient must be the token endpoint");
    }

    @Test
    public void testTokenEndpointErrorThrowsException() {
        stubTokenEndpointWithStatus(TOKEN_ENDPOINT, 400, null);

        assertThrows(ConnectorIOException.class, () -> createConnector(rsaKeyPair.getPrivate()).test());
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 1);
    }

    @Test
    public void testInvalidPrivateKeyThrowsException() {
        var config = new BaseSamlConfig(
                wireMockServer.port(), API_ENDPOINT,
                "http://localhost:" + wireMockServer.port() + TOKEN_ENDPOINT,
                CLIENT_ID, new GuardedString("not-a-valid-pem-key".toCharArray()));
        var connector = new OAuth2SamlRestConnector();
        connector.init(config);

        assertThrows(ConnectorException.class, connector::test);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 0);
    }

    // --- helpers ---

    private String captureAssertion(String tokenEndpoint) {
        var requests = wireMockServer.findAll(postRequestedFor(urlEqualTo(tokenEndpoint)));
        assertFalse(requests.isEmpty(), "No requests to " + tokenEndpoint);
        for (String param : requests.get(0).getBodyAsString().split("&")) {
            if (param.startsWith("assertion=")) {
                String base64url = URLDecoder.decode(param.substring("assertion=".length()), StandardCharsets.UTF_8);
                return decodeAssertion(base64url);
            }
        }
        throw new IllegalStateException("No assertion in request body");
    }

    private OAuth2SamlRestConnector createConnector(java.security.PrivateKey key) {
        return createConnector(configBuilder(key));
    }

    private OAuth2SamlRestConnector createConnector(BaseSamlConfig config) {
        var connector = new OAuth2SamlRestConnector();
        connector.init(config);
        return connector;
    }

    BaseSamlConfig configBuilder(java.security.PrivateKey key) {
        return new BaseSamlConfig(
                wireMockServer.port(), API_ENDPOINT,
                "http://localhost:" + wireMockServer.port() + TOKEN_ENDPOINT,
                CLIENT_ID, new GuardedString(toPem(key).toCharArray()));
    }
}
