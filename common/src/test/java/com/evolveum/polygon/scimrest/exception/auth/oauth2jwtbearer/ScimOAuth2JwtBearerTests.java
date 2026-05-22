/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.exception.auth.oauth2jwtbearer;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;

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
}
