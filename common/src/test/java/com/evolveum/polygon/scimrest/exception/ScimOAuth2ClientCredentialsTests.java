/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.exception;

import org.identityconnectors.common.security.GuardedString;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.*;

/**
 * Integration tests for SCIM OAuth2 client credentials flow.
 *
 * Verifies that the SCIM Jersey client sends the Bearer token obtained via OAuth2
 * when making requests to SCIM endpoints.
 */
public class ScimOAuth2ClientCredentialsTests extends AbstractScimOAuth2Tests {

    private static final String TOKEN_ENDPOINT = "/oauth2/token";

    @BeforeMethod
    public void setUp() {
        setUpWireMock();
    }

    @AfterMethod
    public void tearDown() {
        tearDownWireMock();
    }

    @Test
    public void testTokenFetchedAndSentInScimRequests() {
        stubTokenEndpoint(TOKEN_ENDPOINT, "scim-token", 3600);
        stubScimEndpoints("scim-token");

        createScimConnector(TOKEN_ENDPOINT, "test-client", new GuardedString("test-secret".toCharArray())).schema();

        assertEquals(wireMockServer.findAll(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))).size(), 1);
        assertTrue(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(SCHEMAS_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer scim-token"))).size() >= 1);
    }

    @Test
    public void testTokenReusedOnSubsequentScimCalls() {
        stubTokenEndpoint(TOKEN_ENDPOINT, "reused-scim-token", 3600);
        stubScimEndpoints("reused-scim-token");

        var connector = createScimConnector(TOKEN_ENDPOINT, "test-client", new GuardedString("test-secret".toCharArray()));
        connector.schema();
        connector.schema();

        assertEquals(wireMockServer.findAll(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))).size(), 1);
    }

    @Test
    public void testTokenRequestContainsCorrectFormParameters() {
        stubTokenEndpoint(TOKEN_ENDPOINT, "param-token", 3600);
        stubScimEndpoints("param-token");

        createScimConnector(TOKEN_ENDPOINT, "my-scim-client", new GuardedString("my-scim-secret".toCharArray())).schema();

        assertTrue(wireMockServer.findAll(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))
                .withRequestBody(containing("grant_type=client_credentials"))
                .withRequestBody(containing("client_id=my-scim-client"))
                .withRequestBody(containing("client_secret=my-scim-secret"))).size() >= 1);
    }
}
