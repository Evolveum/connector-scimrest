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
 * Automated tests for OAuth2 Groovy hook customization in the SCIM context.
 *
 * Covers all four hook points: validateToken, buildTokenRequest, parseTokenResponse, applyToken.
 * Verification is done via SCIM schema discovery requests (Authorization header on /Schemas).
 */
public class ScimOAuth2GroovyHookTests extends WireMockTestSupport {

    private static final String TOKEN_ENDPOINT     = "/oauth/token";
    private static final String SCIM_BASE_PATH     = "/scim";
    private static final String SCHEMAS_ENDPOINT   = SCIM_BASE_PATH + "/Schemas";
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

    /**
     * applyToken hook sets a custom header — verifies it reaches SCIM requests via JerseyRequestCustomizerFilter.
     */
    @Test
    public void testApplyTokenHookUsesCustomHeaderInScimRequests() {
        stubTokenEndpoint("scim-hook-token", 3600);
        stubScimEndpoints("scim-hook-token");

        var script = """
                authentication {
                    scim {
                        oauth2 { oauth2Context ->
                            applyToken { request ->
                                request.header("Authorization", "Bearer ${oauth2Context['access_token']}")
                            }
                        }
                    }
                }
                """;

        createConnector(script).schema();

        assertTrue(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(SCHEMAS_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer scim-hook-token"))).size() >= 1);
    }

    /**
     * buildTokenRequest hook adds a custom form parameter to the token request.
     */
    @Test
    public void testBuildTokenRequestHookAddsCustomParameter() {
        stubTokenEndpointMatchingBody("custom_param=scim-extra", "scim-custom-token", 3600);
        stubScimEndpoints("scim-custom-token");

        var script = """
                authentication {
                    scim {
                        oauth2 { oauth2Context ->
                            buildTokenRequest { request ->
                                request.formParam("grant_type",   "client_credentials")
                                request.formParam("client_id",    configuration.clientId)
                                request.formParam("custom_param", "scim-extra")
                            }
                        }
                    }
                }
                """;

        createConnector(script).schema();

        assertEquals(wireMockServer.findAll(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))
                .withRequestBody(containing("custom_param=scim-extra"))).size(), 1);
    }

    /**
     * parseTokenResponse hook extracts the token from a non-standard nested response structure.
     */
    @Test
    public void testParseTokenResponseHookHandlesCustomResponseStructure() {
        wireMockServer.stubFor(post(urlEqualTo(TOKEN_ENDPOINT))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"data\":{\"token\":\"scim-nested-token\"},\"ok\":true}")));
        stubScimEndpoints("scim-nested-token");

        var script = """
                authentication {
                    scim {
                        oauth2 { oauth2Context ->
                            buildTokenRequest { request ->
                                request.formParam("grant_type", "client_credentials")
                            }
                            parseTokenResponse { response ->
                                oauth2Context["access_token"] = response.data?.token
                            }
                            applyToken { request ->
                                request.header("Authorization", "Bearer ${oauth2Context['access_token']}")
                            }
                        }
                    }
                }
                """;

        createConnector(script).schema();

        assertTrue(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(SCHEMAS_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer scim-nested-token"))).size() >= 1);
    }

    /**
     * All four hooks: validateToken seeds an expired token, buildTokenRequest uses refresh_token grant,
     * parseTokenResponse extracts new token, applyToken sets Authorization header on SCIM requests.
     */
    @Test
    public void testAllFourHooksViaRefreshTokenFlow() {
        wireMockServer.stubFor(post(urlEqualTo(TOKEN_ENDPOINT))
                .withRequestBody(containing("grant_type=refresh_token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"scim-refreshed-token\",\"expires_in\":3600}")));
        stubScimEndpoints("scim-refreshed-token");

        var script = """
                authentication {
                    scim {
                        oauth2 { oauth2Context ->
                            validateToken {
                                if (!oauth2Context["access_token"]) {
                                    oauth2Context["access_token"]  = "mocked-expired-token"
                                    oauth2Context["expires_at"]    = java.time.Instant.now().minusSeconds(300)
                                    oauth2Context["refresh_token"] = "mocked-refresh-token"
                                    return false
                                }
                                def expiresAt = oauth2Context["expires_at"]
                                expiresAt != null && expiresAt.isAfter(java.time.Instant.now())
                            }
                            buildTokenRequest { request ->
                                if (oauth2Context["refresh_token"]) {
                                    request.formParam("grant_type",    "refresh_token")
                                    request.formParam("refresh_token", oauth2Context["refresh_token"])
                                }
                            }
                            parseTokenResponse { response ->
                                oauth2Context["access_token"] = response.access_token
                                oauth2Context["expires_in"]   = response.expires_in ?: 3600
                            }
                            applyToken { request ->
                                request.header("Authorization", "Bearer ${oauth2Context['access_token']}")
                            }
                        }
                    }
                }
                """;

        createConnector(script).schema();

        assertEquals(wireMockServer.findAll(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))
                .withRequestBody(containing("grant_type=refresh_token"))
                .withRequestBody(containing("refresh_token=mocked-refresh-token"))).size(), 1);
        assertTrue(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(SCHEMAS_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer scim-refreshed-token"))).size() >= 1);
    }

    // --- helpers ---

    private AbstractGroovyRestConnector<?> createConnector(String groovyScript) {
        var config = new ScimOAuth2HookTestConfig(wireMockServer.port());
        var connector = new ScimOAuth2HookTestConnector(groovyScript);
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

    private void stubTokenEndpointMatchingBody(String bodyContains, String accessToken, int expiresIn) {
        wireMockServer.stubFor(post(urlEqualTo(TOKEN_ENDPOINT))
                .withRequestBody(containing(bodyContains))
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

    private static class ScimOAuth2HookTestConfig extends BaseGroovyConnectorConfiguration
            implements ScimClientConfiguration.OAuth2Authorization {

        private final int port;

        ScimOAuth2HookTestConfig(int port) {
            this.port = port;
        }

        @Override public String getScimBaseUrl() { return "http://localhost:" + port + SCIM_BASE_PATH; }
        @Override public String getScimOAuthTokenUrl() { return "http://localhost:" + port + TOKEN_ENDPOINT; }
        @Override public String getScimOAuthClientId() { return "test-client"; }
        @Override public GuardedString getScimOAuthClientSecret() {
            return new GuardedString("test-secret".toCharArray());
        }
        @Override public GuardedString getScimOAuthPrivateKey() { return null; }
    }

    private static class ScimOAuth2HookTestConnector
            extends AbstractGroovyRestConnector<BaseGroovyConnectorConfiguration> {

        private final String groovyScript;

        ScimOAuth2HookTestConnector(String groovyScript) {
            super(false);
            this.groovyScript = groovyScript;
        }

        @Override protected void initializeSchema(GroovySchemaLoader loader) {}

        @Override
        protected void initializeObjectClassHandler(GroovyRestHandlerBuilder builder) {
            if (groovyScript != null) {
                builder.loadFromString(groovyScript);
            }
        }
    }
}
