/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.auth.method.oauth2.saml;

import org.identityconnectors.common.security.GuardedString;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.*;

public class ScimOAuth2SamlTests extends AbstractScimOAuth2SamlTests {

    private static final String TOKEN_ENDPOINT = "/oauth2/token";
    private static final String CLIENT_ID = "test-scim-client";

    private KeyPair keyPair;

    @BeforeMethod
    public void setUp() throws Exception {
        setUpWireMock();
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keyPair = gen.generateKeyPair();
    }

    @AfterMethod
    public void tearDown() {
        tearDownWireMock();
    }

    @Test
    public void testTokenFetchedAndSentInScimRequests() {
        stubTokenEndpointWithStatus(TOKEN_ENDPOINT, 200, "scim-saml-token");
        stubScimEndpoints("scim-saml-token");

        createScimConnector(keyPair.getPrivate(), TOKEN_ENDPOINT, CLIENT_ID).schema();

        assertEquals(wireMockServer.findAll(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))).size(), 1);
        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(SCHEMAS_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer scim-saml-token"))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 3);
    }

    @Test
    public void testTokenReusedOnSubsequentScimCalls() {
        stubTokenEndpointWithStatus(TOKEN_ENDPOINT, 200, "reused-scim-saml-token");
        stubScimEndpoints("reused-scim-saml-token");

        var connector = createScimConnector(keyPair.getPrivate(), TOKEN_ENDPOINT, CLIENT_ID);
        connector.schema();
        connector.schema();

        assertEquals(wireMockServer.findAll(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 3);
    }

    @Test
    public void testTokenRequestContainsSamlGrantType() {
        stubTokenEndpointWithStatus(TOKEN_ENDPOINT, 200, "scim-saml-grant-token");
        stubScimEndpoints("scim-saml-grant-token");

        createScimConnector(keyPair.getPrivate(), TOKEN_ENDPOINT, CLIENT_ID).schema();

        wireMockServer.verify(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))
                .withRequestBody(containing("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Asaml2-bearer"))
                .withRequestBody(containing("client_id=" + CLIENT_ID))
                .withRequestBody(containing("assertion=")));
    }

    @Test
    public void testCustomIssuerInScimAssertion() {
        stubTokenEndpointWithStatus(TOKEN_ENDPOINT, 200, "scim-issuer-saml-token");
        stubScimEndpoints("scim-issuer-saml-token");

        var config = new ScimSamlTestConfig(wireMockServer.port(), TOKEN_ENDPOINT,
                CLIENT_ID, new GuardedString(toPem(keyPair.getPrivate()).toCharArray()));
        config.setIssuer("scim-issuer@example.com");
        createScimConnector(config, null).schema();

        String assertionXml = captureScimAssertionXml(TOKEN_ENDPOINT);
        assertTrue(assertionXml.contains("scim-issuer@example.com"));
    }

    @Test
    public void testScopeIsSentInScimTokenRequest() {
        stubTokenEndpointWithStatus(TOKEN_ENDPOINT, 200, "scim-scoped-saml-token");
        stubScimEndpoints("scim-scoped-saml-token");

        var config = new ScimSamlTestConfig(wireMockServer.port(), TOKEN_ENDPOINT,
                CLIENT_ID, new GuardedString(toPem(keyPair.getPrivate()).toCharArray()));
        config.setScope("read write");
        createScimConnector(config, null).schema();

        wireMockServer.verify(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))
                .withRequestBody(containing("scope=read+write")));
    }

    @Test
    public void testConnectorFailsWhenTokenEndpointReturns400() {
        stubTokenEndpointWithStatus(TOKEN_ENDPOINT, 400, null);

        try {
            createScimConnector(keyPair.getPrivate(), TOKEN_ENDPOINT, CLIENT_ID).schema();
            fail("Expected ConnectorException was not thrown");
        } catch (Exception e) {
            assertTrue(e instanceof org.identityconnectors.framework.common.exceptions.ConnectorException,
                    "Expected ConnectorException but got: " + e.getClass().getName());
        }
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 1);
    }

    // --- helpers ---

    private String captureScimAssertionXml(String tokenEndpoint) {
        var requests = wireMockServer.findAll(postRequestedFor(urlEqualTo(tokenEndpoint)));
        assertFalse(requests.isEmpty());
        for (String param : requests.get(0).getBodyAsString().split("&")) {
            if (param.startsWith("assertion=")) {
                String base64url = URLDecoder.decode(param.substring("assertion=".length()), StandardCharsets.UTF_8);
                return decodeAssertion(base64url);
            }
        }
        throw new IllegalStateException("No assertion in request body");
    }
}
