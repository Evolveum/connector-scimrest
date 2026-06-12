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

import com.github.tomakehurst.wiremock.stubbing.Scenario;

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
    public void testScopeIsSentInTokenRequest() {
        stubTokenEndpoint(TOKEN_ENDPOINT, "scoped-token", 3600);
        stubRestApiEndpoint(API_ENDPOINT, "scoped-token");

        createConnectorWithExtras("client-id", new GuardedString("secret".toCharArray()),
                "read write", null).test();

        assertEquals(requestCount(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))
                .withRequestBody(containing("scope=read+write"))), 1);
    }

    @Test
    public void testUnsupportedTokenTypeFails() {
        wireMockServer.stubFor(post(urlEqualTo(TOKEN_ENDPOINT))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"t\",\"token_type\":\"mac\",\"expires_in\":3600}")));

        try {
            createConnector("client-id", new GuardedString("secret".toCharArray())).test();
            fail("Expected ConnectorIOException for unsupported token_type");
        } catch (org.identityconnectors.framework.common.exceptions.ConnectorIOException e) {
            assertTrue(e.getMessage().contains("mac"));
        }
    }

    @Test
    public void testClientAuthenticationSchemeBasic() {
        stubTokenEndpoint(TOKEN_ENDPOINT, "basic-scheme-token", 3600);
        stubRestApiEndpoint(API_ENDPOINT, "basic-scheme-token");

        createConnectorWithExtras("my-client", new GuardedString("my-secret".toCharArray()),
                null, "basic").test();

        // credentials in Basic header, NOT in body
        String expectedBasic = java.util.Base64.getEncoder()
                .encodeToString("my-client:my-secret".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(requestCount(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))
                .withHeader("Authorization", equalTo("Basic " + expectedBasic))), 1);
        assertEquals(requestCount(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))
                .withRequestBody(containing("client_id="))), 0);
        assertEquals(requestCount(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))
                .withRequestBody(containing("client_secret="))), 0);
    }

    @Test
    public void testClientAuthenticationSchemePost() {
        stubTokenEndpoint(TOKEN_ENDPOINT, "post-scheme-token", 3600);
        stubRestApiEndpoint(API_ENDPOINT, "post-scheme-token");

        createConnectorWithExtras("my-client", new GuardedString("my-secret".toCharArray()),
                null, "post").test();

        assertEquals(requestCount(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))
                .withRequestBody(containing("client_id=my-client"))
                .withRequestBody(containing("client_secret=my-secret"))), 1);
    }

    @Test
    public void testTokenClearedOn401FromApi() {
        stubTokenEndpoint(TOKEN_ENDPOINT, "test-token", 3600);
        wireMockServer.stubFor(get(urlEqualTo(API_ENDPOINT))
                .inScenario("api-401")
                .whenScenarioStateIs(Scenario.STARTED)
                .withHeader("Authorization", equalTo("Bearer test-token"))
                .willReturn(aResponse().withStatus(401))
                .willSetStateTo("after-401"));
        wireMockServer.stubFor(get(urlEqualTo(API_ENDPOINT))
                .inScenario("api-401")
                .whenScenarioStateIs("after-401")
                .withHeader("Authorization", equalTo("Bearer test-token"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        var connector = createConnector("client-id", new GuardedString("client-secret".toCharArray()));
        try { connector.test(); } catch (Exception ignored) {}
        connector.test();

        assertEquals(requestCount(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))), 2,
                "Token re-fetched after 401 cleared it");
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

    private OAuth2RestConnector createConnectorWithExtras(String clientId, GuardedString clientSecret,
            String scope, String authScheme) {
        var config = new ExtendedClientCredentialsConfig(
                wireMockServer.port(), API_ENDPOINT,
                "http://localhost:" + wireMockServer.port() + TOKEN_ENDPOINT,
                clientId, clientSecret, scope, authScheme);
        var connector = new OAuth2RestConnector();
        connector.init(config);
        return connector;
    }

    private static class ExtendedClientCredentialsConfig extends BaseClientCredentialsConfig {

        private final String scope;
        private final String authScheme;

        ExtendedClientCredentialsConfig(int port, String testEndpoint, String tokenUrl,
                String clientId, GuardedString clientSecret, String scope, String authScheme) {
            super(port, testEndpoint, tokenUrl, clientId, clientSecret);
            this.scope = scope;
            this.authScheme = authScheme;
        }

        @Override
        public String getRestOAuth2Scope() { return scope; }

        @Override
        public String getRestOAuth2ClientAuthenticationScheme() { return authScheme; }
    }
}
