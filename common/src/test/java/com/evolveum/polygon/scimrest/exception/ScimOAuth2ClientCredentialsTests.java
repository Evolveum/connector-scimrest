/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.exception;

import com.evolveum.polygon.scimrest.config.ScimClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.AbstractGroovyRestConnector;
import com.evolveum.polygon.scimrest.groovy.BaseGroovyConnectorConfiguration;
import com.evolveum.polygon.scimrest.groovy.GroovyRestHandlerBuilder;
import com.evolveum.polygon.scimrest.groovy.GroovySchemaLoader;
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
public class ScimOAuth2ClientCredentialsTests extends WireMockTestSupport {

    private static final String TOKEN_ENDPOINT    = "/oauth2/token";
    private static final String SCIM_BASE_PATH    = "/scim";
    private static final String SCHEMAS_ENDPOINT  = SCIM_BASE_PATH + "/Schemas";
    private static final String RESOURCES_ENDPOINT = SCIM_BASE_PATH + "/ResourceTypes";

    private static final String EMPTY_LIST_RESPONSE = """
            {
              "schemas": ["urn:ietf:params:scim:api:messages:2.0:ListResponse"],
              "totalResults": 0,
              "Resources": []
            }
            """;

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
        stubTokenEndpoint("scim-token", 3600);
        stubScimEndpoints("scim-token");

        createConnector().schema();

        assertEquals(wireMockServer.findAll(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))).size(), 1);
        assertTrue(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(SCHEMAS_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer scim-token"))).size() >= 1);
    }

    @Test
    public void testTokenReusedOnSubsequentScimCalls() {
        stubTokenEndpoint("reused-scim-token", 3600);
        stubScimEndpoints("reused-scim-token");

        var connector = createConnector();
        connector.schema();
        connector.schema();

        assertEquals(wireMockServer.findAll(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))).size(), 1);
    }

    @Test
    public void testTokenRequestContainsCorrectFormParameters() {
        stubTokenEndpoint("param-token", 3600);
        stubScimEndpoints("param-token");

        createConnector("my-scim-client", new GuardedString("my-scim-secret".toCharArray())).schema();

        assertTrue(wireMockServer.findAll(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))
                .withRequestBody(containing("grant_type=client_credentials"))
                .withRequestBody(containing("client_id=my-scim-client"))
                .withRequestBody(containing("client_secret=my-scim-secret"))).size() >= 1);
    }

    // --- helpers ---

    private AbstractGroovyRestConnector<?> createConnector() {
        return createConnector("test-client", new GuardedString("test-secret".toCharArray()));
    }

    private AbstractGroovyRestConnector<?> createConnector(String clientId, GuardedString clientSecret) {
        var config = new ScimOAuth2TestConfiguration(wireMockServer.port(), clientId, clientSecret);
        var connector = new ScimOAuth2TestConnector();
        connector.init(config);
        return connector;
    }

    private void stubTokenEndpoint(String accessToken, int expiresIn) {
        wireMockServer.stubFor(post(urlEqualTo(TOKEN_ENDPOINT))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"" + accessToken + "\",\"expires_in\":" + expiresIn + "}")));
    }

    private void stubScimEndpoints(String expectedToken) {
        wireMockServer.stubFor(get(urlPathEqualTo(SCHEMAS_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer " + expectedToken))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/scim+json")
                        .withBody(EMPTY_LIST_RESPONSE)));
        wireMockServer.stubFor(get(urlPathEqualTo(RESOURCES_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer " + expectedToken))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/scim+json")
                        .withBody(EMPTY_LIST_RESPONSE)));
    }

    // --- test doubles ---

    private static class ScimOAuth2TestConfiguration extends BaseGroovyConnectorConfiguration
            implements ScimClientConfiguration.OAuth2Authorization {

        private final int port;
        private final String clientId;
        private final GuardedString clientSecret;

        ScimOAuth2TestConfiguration(int port, String clientId, GuardedString clientSecret) {
            this.port = port;
            this.clientId = clientId;
            this.clientSecret = clientSecret;
        }

        @Override public String getScimBaseUrl() { return "http://localhost:" + port + SCIM_BASE_PATH; }
        @Override public String getScimOAuthTokenUrl() { return "http://localhost:" + port + TOKEN_ENDPOINT; }
        @Override public String getScimOAuthClientId() { return clientId; }
        @Override public GuardedString getScimOAuthClientSecret() { return clientSecret; }
        @Override public GuardedString getScimOAuthPrivateKey() { return null; }
    }

    private static class ScimOAuth2TestConnector extends AbstractGroovyRestConnector<BaseGroovyConnectorConfiguration> {

        ScimOAuth2TestConnector() {
            super(false);
        }

        @Override protected void initializeSchema(GroovySchemaLoader loader) {}
        @Override protected void initializeObjectClassHandler(GroovyRestHandlerBuilder builder) {}
    }
}
