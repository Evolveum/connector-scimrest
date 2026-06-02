/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.exception.auth.oauth2clientcredentials;

import com.evolveum.polygon.scimrest.config.ScimClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.BaseRestGroovyConnectorConfiguration;
import org.identityconnectors.common.security.GuardedString;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.*;

public class ScimOAuth2ClientCredentialsTests extends AbstractScimOAuth2ClientCredentialsTests {

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
        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(SCHEMAS_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer scim-token"))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 3);
    }

    @Test
    public void testTokenReusedOnSubsequentScimCalls() {
        stubTokenEndpoint(TOKEN_ENDPOINT, "reused-scim-token", 3600);
        stubScimEndpoints("reused-scim-token");

        var connector = createScimConnector(TOKEN_ENDPOINT, "test-client", new GuardedString("test-secret".toCharArray()));
        connector.schema();
        connector.schema();

        assertEquals(wireMockServer.findAll(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 3);
    }

    @Test
    public void testTokenRequestContainsCorrectFormParameters() {
        stubTokenEndpoint(TOKEN_ENDPOINT, "param-token", 3600);
        stubScimEndpoints("param-token");

        createScimConnector(TOKEN_ENDPOINT, "my-scim-client", new GuardedString("my-scim-secret".toCharArray())).schema();

        assertEquals(wireMockServer.findAll(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))
                .withRequestBody(containing("grant_type=client_credentials"))
                .withRequestBody(containing("client_id=my-scim-client"))
                .withRequestBody(containing("client_secret=my-scim-secret"))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 3);
    }

    @Test
    public void testOAuth2TokenSentToTestEndpoint() {
        String testEndpoint = "/health";
        stubTokenEndpoint(TOKEN_ENDPOINT, "endpoint-scim-token", 3600);
        stubScimEndpoints("endpoint-scim-token");
        wireMockServer.stubFor(get(urlEqualTo(SCIM_BASE_PATH + testEndpoint))
                .willReturn(aResponse().withStatus(200)));

        var config = new ScimOAuth2RestConfig(wireMockServer.port(), TOKEN_ENDPOINT, "my-client",
                new GuardedString("my-secret".toCharArray()));
        config.setRestTestEndpoint(testEndpoint);
        var connector = new ScimOAuth2TestConnector(null);
        connector.init(config);
        connector.test();

        assertEquals(wireMockServer.findAll(getRequestedFor(urlEqualTo(SCIM_BASE_PATH + testEndpoint))
                .withHeader("Authorization", equalTo("Bearer endpoint-scim-token"))).size(), 1);
        // 1 token fetch + 2 SCIM discovery + 1 test endpoint
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 4);
    }

    @Test
    public void testConnectorFailsWhenTokenEndpointReturns401() {
        stubTokenEndpointError(TOKEN_ENDPOINT, 401);

        try {
            createScimConnector(TOKEN_ENDPOINT, "bad-client",
                    new GuardedString("bad-secret".toCharArray())).schema();
            fail("Expected ConnectorException was not thrown");
        } catch (Exception e) {
            assertTrue(e instanceof org.identityconnectors.framework.common.exceptions.ConnectorException,
                    "Expected ConnectorException but got: " + e.getClass().getName());
        }
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 1);
    }

    // --- config class for test endpoint tests ---

    private class ScimOAuth2RestConfig extends BaseRestGroovyConnectorConfiguration
            implements ScimClientConfiguration.OAuth2ClientCredentialsAuthorization {

        private final int port;
        private final String tokenEndpoint;
        private final String clientId;
        private final GuardedString clientSecret;

        ScimOAuth2RestConfig(int port, String tokenEndpoint, String clientId, GuardedString clientSecret) {
            this.port = port;
            this.tokenEndpoint = tokenEndpoint;
            this.clientId = clientId;
            this.clientSecret = clientSecret;
            setBaseAddress("http://localhost:" + port);
        }

        @Override
        public String getScimBaseUrl() {
            return "http://localhost:" + port + SCIM_BASE_PATH;
        }

        @Override
        public String getScimOAuth2TokenUrl() {
            return "http://localhost:" + port + tokenEndpoint;
        }

        @Override
        public String getScimOAuth2ClientId() {
            return clientId;
        }

        @Override
        public GuardedString getScimOAuth2ClientSecret() {
            return clientSecret;
        }

        @Override
        public String getScimOAuth2Scope() {
            return null;
        }

        @Override
        public String getScimOAuth2ClientAuthenticationScheme() {
            return null;
        }
    }
}
