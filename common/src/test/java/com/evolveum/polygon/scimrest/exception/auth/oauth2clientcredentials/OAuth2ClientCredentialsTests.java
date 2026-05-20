/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.exception.auth.oauth2clientcredentials;

import org.identityconnectors.common.security.GuardedString;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.*;

import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;

public class OAuth2ClientCredentialsTests extends AbstractOAuth2ClientCredentialsTests {

    private static final String TOKEN_ENDPOINT = "/oauth2/token";
    private static final String API_ENDPOINT = "/api/resource";

    @BeforeMethod
    public void setUp() {
        setUpWireMock();
    }

    @AfterMethod
    public void tearDown() {
        tearDownWireMock();
    }

    @Test
    public void testTokenFetchedOnFirstRequest() {
        stubTokenEndpoint(TOKEN_ENDPOINT, "first-access-token", 3600);
        stubRestApiEndpoint(API_ENDPOINT, "first-access-token");

        createConnector("client-id", new GuardedString("client-secret".toCharArray())).test();

        assertEquals(requestCount(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))), 1);
        assertEquals(requestCount(getRequestedFor(urlEqualTo(API_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer first-access-token"))), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 2);
    }

    @Test
    public void testTokenReusedOnSubsequentRequests() {
        stubTokenEndpoint(TOKEN_ENDPOINT, "reused-token", 3600);
        stubRestApiEndpoint(API_ENDPOINT, "reused-token");

        var connector = createConnector("client-id", new GuardedString("client-secret".toCharArray()));
        connector.test();
        connector.test();

        assertEquals(requestCount(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))), 1);
        assertEquals(requestCount(getRequestedFor(urlEqualTo(API_ENDPOINT))), 2);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 3);
    }

    @Test
    public void testTokenRefreshedWhenExpired() {
        stubTokenEndpoint(TOKEN_ENDPOINT, "expired-token", -1);
        stubRestApiEndpoint(API_ENDPOINT, "expired-token");

        var connector = createConnector("client-id", new GuardedString("client-secret".toCharArray()));
        connector.test();

        stubTokenEndpoint(TOKEN_ENDPOINT, "refreshed-token", 3600);
        stubRestApiEndpoint(API_ENDPOINT, "refreshed-token");
        connector.test();

        assertEquals(requestCount(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))), 2);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 4);
    }

    @Test
    public void testTokenRequestContainsCorrectFormParameters() {
        stubTokenEndpoint(TOKEN_ENDPOINT, "scoped-token", 3600);
        stubRestApiEndpoint(API_ENDPOINT, "scoped-token");

        createConnector("my-client-id", new GuardedString("my-secret".toCharArray())).test();

        assertEquals(requestCount(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))
                .withRequestBody(containing("grant_type=client_credentials"))
                .withRequestBody(containing("client_id=my-client-id"))
                .withRequestBody(containing("client_secret=my-secret"))), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 2);
    }

    @Test
    public void testConnectorFailsWhenTokenEndpointReturns401() {
        stubTokenEndpointError(TOKEN_ENDPOINT, 401);

        try {
            createConnector("bad-client", new GuardedString("bad-secret".toCharArray())).test();
            fail("Expected ConnectorIOException was not thrown");
        } catch (Exception e) {
            assertTrue(e instanceof org.identityconnectors.framework.common.exceptions.ConnectorIOException,
                    "Expected ConnectorIOException but got: " + e.getClass().getName());
            assertTrue(e.getMessage().contains("401"),
                    "Error message should contain HTTP status code");
        }
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 1);
    }

    @Test
    public void testRefreshTokenUsedWhenServerProvidesOne() {
        stubTokenEndpoint(TOKEN_ENDPOINT, "first-token", -1, "client_credentials", "my-refresh-token");
        stubRestApiEndpoint(API_ENDPOINT, "first-token");
        stubTokenEndpoint(TOKEN_ENDPOINT, "refreshed-token", 3600, "refresh_token", null);
        stubRestApiEndpoint(API_ENDPOINT, "refreshed-token");

        var connector = createConnector("client-id", new GuardedString("client-secret".toCharArray()));
        connector.test();
        connector.test();

        assertEquals(requestCount(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))
                .withRequestBody(containing("grant_type=client_credentials"))), 1);
        assertEquals(requestCount(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))
                .withRequestBody(containing("grant_type=refresh_token"))), 1);
        assertEquals(requestCount(getRequestedFor(urlEqualTo(API_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer refreshed-token"))), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 4);
    }

    @Test
    public void testFallsBackToClientCredentialsAfterRefreshTokenFailure() {
        stubTokenEndpoint(TOKEN_ENDPOINT, "first-token", -1, "client_credentials", "expired-refresh");
        stubRestApiEndpoint(API_ENDPOINT, "first-token");
        stubTokenEndpointError(TOKEN_ENDPOINT, 400, "refresh_token");

        var connector = createConnector("client-id", new GuardedString("client-secret".toCharArray()));
        connector.test();

        try {
            connector.test();
            fail("Expected ConnectorIOException was not thrown");
        } catch (org.identityconnectors.framework.common.exceptions.ConnectorIOException e) {
            assertTrue(e.getMessage().contains("400"));
        }

        stubTokenEndpoint(TOKEN_ENDPOINT, "new-token", 3600, "client_credentials", null);
        stubRestApiEndpoint(API_ENDPOINT, "new-token");

        connector.test();

        assertEquals(requestCount(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))
                .withRequestBody(containing("grant_type=refresh_token"))), 1);
        assertEquals(requestCount(getRequestedFor(urlEqualTo(API_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer new-token"))), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 5);
    }

    // --- helpers ---

    private int requestCount(RequestPatternBuilder pattern) {
        return wireMockServer.findAll(pattern).size();
    }

    private OAuth2RestConnector createConnector(String clientId, GuardedString clientSecret) {
        var config = new BaseClientCredentialsConfig(
                wireMockServer.port(), API_ENDPOINT,
                "http://localhost:" + wireMockServer.port() + TOKEN_ENDPOINT,
                clientId, clientSecret);
        var connector = new OAuth2RestConnector();
        connector.init(config);
        return connector;
    }
}
