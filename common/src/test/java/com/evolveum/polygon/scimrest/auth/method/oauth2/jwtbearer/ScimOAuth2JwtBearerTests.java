/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.auth.method.oauth2.jwtbearer;

import org.identityconnectors.common.security.GuardedString;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.*;

public class ScimOAuth2JwtBearerTests extends AbstractScimOAuth2JwtBearerTests {

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
        stubTokenEndpointWithStatus(TOKEN_ENDPOINT, 200, "scim-jwt-token");
        stubScimEndpoints("scim-jwt-token");

        createScimConnector(keyPair.getPrivate(), TOKEN_ENDPOINT, CLIENT_ID).schema();

        assertEquals(wireMockServer.findAll(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))).size(), 1);
        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(SCHEMAS_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer scim-jwt-token"))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 3);
    }

    @Test
    public void testTokenReusedOnSubsequentScimCalls() {
        stubTokenEndpointWithStatus(TOKEN_ENDPOINT, 200, "reused-scim-jwt-token");
        stubScimEndpoints("reused-scim-jwt-token");

        var connector = createScimConnector(keyPair.getPrivate(), TOKEN_ENDPOINT, CLIENT_ID);
        connector.schema();
        connector.schema();

        assertEquals(wireMockServer.findAll(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 3);
    }

    @Test
    public void testTokenRequestContainsJwtAssertionParams() {
        stubTokenEndpointWithStatus(TOKEN_ENDPOINT, 200, "param-jwt-token");
        stubScimEndpoints("param-jwt-token");

        createScimConnector(keyPair.getPrivate(), TOKEN_ENDPOINT, CLIENT_ID).schema();

        assertEquals(wireMockServer.findAll(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))
                .withRequestBody(containing("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer"))
                .withRequestBody(containing("client_id=" + CLIENT_ID))
                .withRequestBody(containing("assertion="))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 3);
    }

    @Test
    public void testCustomIssuerAndSubjectInScimJwt() {
        stubTokenEndpointWithStatus(TOKEN_ENDPOINT, 200, "scim-issuer-token");
        stubScimEndpoints("scim-issuer-token");

        var config = new ScimJwtBearerTestConfig(wireMockServer.port(), TOKEN_ENDPOINT,
                CLIENT_ID, new GuardedString(toPem(keyPair.getPrivate()).toCharArray()));
        config.setIssuer("custom-issuer");
        config.setSubject("svc@example.com");
        createScimConnector(config, null).schema();

        String payload = decodeJwtPart(captureScimAssertion(), 1);
        assertTrue(payload.contains("\"iss\":\"custom-issuer\""));
        assertTrue(payload.contains("\"sub\":\"svc@example.com\""));
    }

    @Test
    public void testKidHeaderInScimJwt() {
        stubTokenEndpointWithStatus(TOKEN_ENDPOINT, 200, "scim-kid-token");
        stubScimEndpoints("scim-kid-token");

        var config = new ScimJwtBearerTestConfig(wireMockServer.port(), TOKEN_ENDPOINT,
                CLIENT_ID, new GuardedString(toPem(keyPair.getPrivate()).toCharArray()));
        config.setKeyId("scim-key-1");
        createScimConnector(config, null).schema();

        String header = decodeJwtPart(captureScimAssertion(), 0);
        assertTrue(header.contains("\"kid\":\"scim-key-1\""));
    }

    @Test
    public void testScopeIsSentInScimTokenRequest() {
        stubTokenEndpointWithStatus(TOKEN_ENDPOINT, 200, "scim-scoped-token");
        stubScimEndpoints("scim-scoped-token");

        var config = new ScimJwtBearerTestConfig(wireMockServer.port(), TOKEN_ENDPOINT,
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

    private String captureScimAssertion() {
        var requests = wireMockServer.findAll(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT)));
        assertFalse(requests.isEmpty());
        for (String param : requests.get(0).getBodyAsString().split("&")) {
            if (param.startsWith("assertion=")) {
                return URLDecoder.decode(param.substring("assertion=".length()), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("No assertion in request body");
    }

    private String decodeJwtPart(String jwt, int index) {
        return new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[index]), StandardCharsets.UTF_8);
    }
}
