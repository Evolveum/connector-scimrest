/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.exception;

import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.AbstractGroovyRestConnector;
import com.evolveum.polygon.scimrest.groovy.BaseRestGroovyConnectorConfiguration;
import com.evolveum.polygon.scimrest.groovy.GroovyRestHandlerBuilder;
import com.evolveum.polygon.scimrest.groovy.GroovySchemaLoader;
import org.identityconnectors.common.security.GuardedString;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.*;

import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;

/**
 * Tests for OAuth2 Client Credentials flow: token fetch, reuse, and refresh.
 */
public class OAuth2AuthenticationTests extends WireMockTestSupport {

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
        stubTokenEndpoint("first-access-token", 3600);
        stubApiEndpoint("first-access-token");

        var connector = createConnector(wireMockServer.port(), TOKEN_ENDPOINT, "client-id",
                new GuardedString("client-secret".toCharArray()), null);
        connector.test();

        assertEquals(requestCount(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))), 1);
        assertEquals(requestCount(getRequestedFor(urlEqualTo(API_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer first-access-token"))), 1);
    }

    @Test
    public void testTokenReusedOnSubsequentRequests() {
        stubTokenEndpoint("reused-token", 3600);
        stubApiEndpoint("reused-token");

        var connector = createConnector(wireMockServer.port(), TOKEN_ENDPOINT, "client-id",
                new GuardedString("client-secret".toCharArray()), null);

        connector.test();
        connector.test();

        // Token endpoint called only once - token was reused
        assertEquals(requestCount(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))), 1);
        assertEquals(requestCount(getRequestedFor(urlEqualTo(API_ENDPOINT))), 2);
    }

    @Test
    public void testTokenRefreshedWhenExpired() throws InterruptedException {
        // expires_in=1 with EXPIRY_BUFFER_SECONDS=60 → token immediately expired on second call
        stubTokenEndpoint("expired-token", 1);
        stubApiEndpoint("expired-token"); // returns 200 even for expired token — API behavior is not under test here

        var connector = createConnector(wireMockServer.port(), TOKEN_ENDPOINT, "client-id",
                new GuardedString("client-secret".toCharArray()), null);
        connector.test();

        stubTokenEndpoint("refreshed-token", 3600);
        stubApiEndpoint("refreshed-token");
        connector.test();

        // Token endpoint called twice - once for initial fetch, once for refresh
        assertEquals(requestCount(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))), 2);
    }

    @Test
    public void testTokenRequestContainsCorrectFormParameters() {
        stubTokenEndpoint("scoped-token", 3600);
        stubApiEndpoint("scoped-token");

        var connector = createConnector(wireMockServer.port(), TOKEN_ENDPOINT, "my-client-id",
                new GuardedString("my-secret".toCharArray()), "read write");
        connector.test();

        assertTrue(requestCount(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))
                .withRequestBody(containing("grant_type=client_credentials"))
                .withRequestBody(containing("client_id=my-client-id"))
                .withRequestBody(containing("client_secret=my-secret"))
                .withRequestBody(containing("scope=read+write"))) >= 1);
    }

    @Test
    public void testConnectorFailsWhenTokenEndpointReturns401() {
        stubTokenEndpoint(401);

        var connector = createConnector(wireMockServer.port(), TOKEN_ENDPOINT, "bad-client",
                new GuardedString("bad-secret".toCharArray()), null);

        try {
            connector.test();
            fail("Expected ConnectorIOException was not thrown");
        } catch (Exception e) {
            assertTrue(e instanceof org.identityconnectors.framework.common.exceptions.ConnectorIOException,
                    "Expected ConnectorIOException but got: " + e.getClass().getName());
            assertTrue(e.getMessage().contains("401"),
                    "Error message should contain HTTP status code");
        }
    }

    @Test
    public void testRefreshTokenUsedWhenServerProvidesOne() {
        stubTokenEndpoint("first-token", 1, "client_credentials", "my-refresh-token");
        stubApiEndpoint("first-token"); // returns 200 even for expired token — API behavior is not under test here
        stubTokenEndpoint("refreshed-token", 3600, "refresh_token", null);
        stubApiEndpoint("refreshed-token");

        var connector = createConnector(wireMockServer.port(), TOKEN_ENDPOINT, "client-id",
                new GuardedString("client-secret".toCharArray()), null);
        connector.test();
        connector.test();

        assertEquals(requestCount(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))
                .withRequestBody(containing("grant_type=client_credentials"))), 1);
        assertEquals(requestCount(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))
                .withRequestBody(containing("grant_type=refresh_token"))), 1);
        assertEquals(requestCount(getRequestedFor(urlEqualTo(API_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer refreshed-token"))), 1);
    }

    @Test
    public void testFallsBackToClientCredentialsAfterRefreshTokenFailure() {
        stubTokenEndpoint("first-token", 1, "client_credentials", "expired-refresh");
        stubApiEndpoint("first-token"); // returns 200 even for expired token — API behavior is not under test here
        stubTokenEndpoint(400, "refresh_token");

        var connector = createConnector(wireMockServer.port(), TOKEN_ENDPOINT, "client-id",
                new GuardedString("client-secret".toCharArray()), null);
        connector.test();

        try {
            connector.test();
            fail("Expected ConnectorIOException was not thrown");
        } catch (org.identityconnectors.framework.common.exceptions.ConnectorIOException e) {
            assertTrue(e.getMessage().contains("400"));
        }

        stubTokenEndpoint("new-token", 3600, "client_credentials", null);
        stubApiEndpoint("new-token");

        connector.test();

        assertEquals(requestCount(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))
                .withRequestBody(containing("grant_type=refresh_token"))), 1);
        assertEquals(requestCount(getRequestedFor(urlEqualTo(API_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer new-token"))), 1);
    }

    @Test
    public void testClientSecretBasicAuthMethod() {
        String expectedBasic = java.util.Base64.getEncoder().encodeToString("my-client:my-secret".getBytes());

        stubTokenEndpoint("basic-auth-token", 3600, null, null, expectedBasic);
        stubApiEndpoint("basic-auth-token");

        var config = new OAuth2TestConfiguration(wireMockServer.port(), TOKEN_ENDPOINT, "my-client",
                new GuardedString("my-secret".toCharArray()), null);
        var connector = new OAuth2TestConnector(config);
        connector.init(config);
        connector.test();

        assertEquals(requestCount(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))
                .withHeader("Authorization", equalTo("Basic " + expectedBasic))), 1);
    }

    // --- helpers ---

    private int requestCount(RequestPatternBuilder pattern) {
        return wireMockServer.findAll(pattern).size();
    }

    private void stubTokenEndpoint(String accessToken, int expiresIn) {
        stubTokenEndpoint(accessToken, expiresIn, null, null, null);
    }

    private void stubTokenEndpoint(String accessToken, int expiresIn, String grantType, String refreshToken) {
        stubTokenEndpoint(accessToken, expiresIn, grantType, refreshToken, null);
    }

    private void stubTokenEndpoint(String accessToken, int expiresIn, String grantType, String refreshToken, String basicAuthHeader) {
        var stub = post(urlEqualTo(TOKEN_ENDPOINT));
        if (grantType != null)
            stub = stub.withRequestBody(containing("grant_type=" + grantType));
        if (basicAuthHeader != null)
            stub = stub.withHeader("Authorization", equalTo("Basic " + basicAuthHeader));
        String body = "{\"access_token\":\"" + accessToken + "\",\"expires_in\":" + expiresIn
                + (refreshToken != null ? ",\"refresh_token\":\"" + refreshToken + "\"" : "") + "}";
        wireMockServer.stubFor(stub.willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(body)));
    }

    private void stubTokenEndpoint(int errorStatus) {
        stubTokenEndpoint(errorStatus, null);
    }

    private void stubTokenEndpoint(int errorStatus, String grantType) {
        var stub = post(urlEqualTo(TOKEN_ENDPOINT));
        if (grantType != null)
            stub = stub.withRequestBody(containing("grant_type=" + grantType));
        wireMockServer.stubFor(stub.willReturn(aResponse()
                .withStatus(errorStatus)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":\"oauth2_error\"}")));
    }

    private void stubApiEndpoint(String expectedToken) {
        wireMockServer.stubFor(get(urlEqualTo(API_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer " + expectedToken))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"ok\"}")));
    }

    private OAuth2TestConnector createConnector(int port, String tokenEndpoint, String clientId,
                                                GuardedString clientSecret, String scope) {
        var config = new OAuth2TestConfiguration(port, tokenEndpoint, clientId, clientSecret, scope);
        var connector = new OAuth2TestConnector(config);
        connector.init(config);
        return connector;
    }

    // --- test doubles ---

    private static class OAuth2TestConfiguration extends BaseRestGroovyConnectorConfiguration
            implements RestClientConfiguration.OAuth2Authorization {

        private final int port;
        private final String tokenEndpoint;
        private final String clientId;
        private final GuardedString clientSecret;
        private final String scope;

        OAuth2TestConfiguration(int port, String tokenEndpoint, String clientId,
                                GuardedString clientSecret, String scope) {
            this.port = port;
            this.tokenEndpoint = tokenEndpoint;
            this.clientId = clientId;
            this.clientSecret = clientSecret;
            this.scope = scope;
        }

        @Override
        public String getBaseAddress() {
            return "http://localhost:" + port;
        }

        @Override
        public String getRestTestEndpoint() {
            return API_ENDPOINT;
        }

        @Override
        public String getRestOAuth2TokenUrl() {
            return "http://localhost:" + port + tokenEndpoint;
        }

        @Override
        public String getRestOAuth2ClientId() {
            return clientId;
        }

        @Override
        public GuardedString getRestOAuth2ClientSecret() {
            return clientSecret;
        }


    }

    private static class OAuth2TestConnector extends AbstractGroovyRestConnector<BaseRestGroovyConnectorConfiguration> {

        private final BaseRestGroovyConnectorConfiguration configuration;

        OAuth2TestConnector(BaseRestGroovyConnectorConfiguration configuration) {
            super(false);
            this.configuration = configuration;
        }

        @Override
        protected void initializeSchema(GroovySchemaLoader loader) {
        }

        @Override
        protected void initializeObjectClassHandler(GroovyRestHandlerBuilder builder) {
        }
    }
}
