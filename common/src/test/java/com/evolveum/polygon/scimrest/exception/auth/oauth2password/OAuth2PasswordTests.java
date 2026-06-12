/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.exception.auth.oauth2password;

import org.identityconnectors.common.security.GuardedString;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.*;

public class OAuth2PasswordTests extends AbstractOAuth2PasswordTests {

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
        stubTokenEndpoint(TOKEN_ENDPOINT, "password-token", 3600);
        stubRestApiEndpoint(API_ENDPOINT, "password-token");

        createConnector("client-id", "alice", new GuardedString("secret".toCharArray())).test();

        assertEquals(wireMockServer.findAll(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))).size(), 1);
        assertEquals(wireMockServer.findAll(getRequestedFor(urlEqualTo(API_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer password-token"))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 2);
    }

    @Test
    public void testTokenReusedOnSubsequentRequests() {
        stubTokenEndpoint(TOKEN_ENDPOINT, "reused-token", 3600);
        stubRestApiEndpoint(API_ENDPOINT, "reused-token");

        var connector = createConnector("client-id", "alice", new GuardedString("secret".toCharArray()));
        connector.test();
        connector.test();

        assertEquals(wireMockServer.findAll(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))).size(), 1);
        assertEquals(wireMockServer.findAll(getRequestedFor(urlEqualTo(API_ENDPOINT))).size(), 2);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 3);
    }

    @Test
    public void testTokenRefreshedWhenExpired() {
        stubTokenEndpoint(TOKEN_ENDPOINT, "expired-token", -1);
        stubRestApiEndpoint(API_ENDPOINT, "expired-token");

        var connector = createConnector("client-id", "alice", new GuardedString("secret".toCharArray()));
        connector.test();

        stubTokenEndpoint(TOKEN_ENDPOINT, "refreshed-token", 3600);
        stubRestApiEndpoint(API_ENDPOINT, "refreshed-token");
        connector.test();

        assertEquals(wireMockServer.findAll(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))).size(), 2);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 4);
    }

    @Test
    public void testTokenRequestContainsCorrectFormParameters() {
        stubTokenEndpoint(TOKEN_ENDPOINT, "param-token", 3600);
        stubRestApiEndpoint(API_ENDPOINT, "param-token");

        createConnector("my-client", "john.doe", new GuardedString("s3cr3t".toCharArray())).test();

        assertEquals(wireMockServer.findAll(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))
                .withRequestBody(containing("grant_type=password"))
                .withRequestBody(containing("client_id=my-client"))
                .withRequestBody(containing("username=john.doe"))
                .withRequestBody(containing("password=s3cr3t"))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 2);
    }

    @Test
    public void testScopeIsSentInTokenRequest() {
        stubTokenEndpoint(TOKEN_ENDPOINT, "scoped-token", 3600);
        stubRestApiEndpoint(API_ENDPOINT, "scoped-token");

        var config = new BasePasswordConfig(wireMockServer.port(), API_ENDPOINT,
                "http://localhost:" + wireMockServer.port() + TOKEN_ENDPOINT,
                "client-id", "alice", new GuardedString("pw".toCharArray()));
        config.setScope("read write");
        var connector = new OAuth2PasswordRestConnector();
        connector.init(config);
        connector.test();

        assertEquals(wireMockServer.findAll(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))
                .withRequestBody(containing("scope=read+write"))).size(), 1);
    }

    @Test
    public void testClientAuthenticationSchemeBasic() {
        stubTokenEndpoint(TOKEN_ENDPOINT, "basic-scheme-token", 3600);
        stubRestApiEndpoint(API_ENDPOINT, "basic-scheme-token");

        var config = new BasePasswordConfig(wireMockServer.port(), API_ENDPOINT,
                "http://localhost:" + wireMockServer.port() + TOKEN_ENDPOINT,
                "my-client", "alice", new GuardedString("pw".toCharArray()));
        config.setAuthScheme("basic");
        var connector = new OAuth2PasswordRestConnector();
        connector.init(config);
        connector.test();

        String expectedBasic = java.util.Base64.getEncoder()
                .encodeToString("my-client:".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(wireMockServer.findAll(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))
                .withHeader("Authorization", equalTo("Basic " + expectedBasic))).size(), 1);
        assertEquals(wireMockServer.findAll(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))
                .withRequestBody(containing("client_id="))).size(), 0);
    }

    @Test
    public void testConnectorFailsWhenTokenEndpointReturns401() {
        stubTokenEndpointError(TOKEN_ENDPOINT, 401);

        try {
            createConnector("bad-client", "alice", new GuardedString("wrong".toCharArray())).test();
            fail("Expected ConnectorIOException was not thrown");
        } catch (Exception e) {
            assertTrue(e instanceof org.identityconnectors.framework.common.exceptions.ConnectorIOException,
                    "Expected ConnectorIOException but got: " + e.getClass().getName());
            assertTrue(e.getMessage().contains("401"));
        }
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 1);
    }

    // --- helpers ---

    private OAuth2PasswordRestConnector createConnector(String clientId, String username, GuardedString password) {
        var config = new BasePasswordConfig(
                wireMockServer.port(), API_ENDPOINT,
                "http://localhost:" + wireMockServer.port() + TOKEN_ENDPOINT,
                clientId, username, password);
        var connector = new OAuth2PasswordRestConnector();
        connector.init(config);
        return connector;
    }
}
