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

/**
 * Automated tests for OAuth2 Groovy hook customization (Part B).
 *
 * Covers all four hook points: validateToken, buildTokenRequest, parseTokenResponse, applyToken.
 */
public class OAuth2GroovyHookTests extends WireMockTestSupport {

    private static final String TOKEN_ENDPOINT = "/oauth/token";
    private static final String API_ENDPOINT   = "/api/verify";

    @BeforeMethod
    public void setUp() {
        setUpWireMock();
    }

    @AfterMethod
    public void tearDown() {
        tearDownWireMock();
    }

    /**
     * Simulates a refresh token flow via all four Groovy hooks — mirrors OAuth2ManualIntegrationTest.
     *
     * validateToken seeds an expired token and a refresh_token, then returns false.
     * buildTokenRequest sends grant_type=refresh_token.
     * parseTokenResponse extracts the new access_token.
     * applyToken adds the Authorization header.
     */
    @Test
    public void testRefreshTokenFlowViaGroovyHooks() {
        stubTokenEndpoint("new-access-token", 3600);
        stubApiEndpoint("new-access-token");

        var script = """
                authentication {
                    rest {
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
                                oauth2Context["access_token"]  = response.access_token
                                oauth2Context["expires_in"]    = response.expires_in ?: 3600
                                if (response.refresh_token) {
                                    oauth2Context["refresh_token"] = response.refresh_token
                                }
                            }

                            applyToken { request ->
                                request.header("Authorization", "Bearer ${oauth2Context['access_token']}")
                            }
                        }
                    }
                }
                """;

        createConnector(script).test();

        assertEquals(wireMockServer.findAll(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))
                .withRequestBody(containing("grant_type=refresh_token"))
                .withRequestBody(containing("refresh_token=mocked-refresh-token"))).size(), 1);

        assertEquals(wireMockServer.findAll(getRequestedFor(urlEqualTo(API_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer new-access-token"))).size(), 1);
    }

    /**
     * buildTokenRequest hook adds a custom form parameter on top of the standard client_credentials flow.
     */
    @Test
    public void testBuildTokenRequestHookAddsCustomParameter() {
        stubTokenEndpointMatchingBody("custom_param=extra-value", "hook-token", 3600);
        stubApiEndpoint("hook-token");

        var script = """
                authentication {
                    rest {
                        oauth2 { oauth2Context ->
                            buildTokenRequest { request ->
                                request.formParam("grant_type",    "client_credentials")
                                request.formParam("client_id",     configuration.clientId)
                                request.formParam("custom_param",  "extra-value")
                            }
                        }
                    }
                }
                """;

        createConnector(script).test();

        assertEquals(wireMockServer.findAll(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))
                .withRequestBody(containing("custom_param=extra-value"))).size(), 1);
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
                        .withBody("{\"data\":{\"token\":\"nested-token\"},\"ok\":true}")));
        stubApiEndpoint("nested-token");

        var script = """
                authentication {
                    rest {
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

        createConnector(script).test();

        assertEquals(wireMockServer.findAll(getRequestedFor(urlEqualTo(API_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer nested-token"))).size(), 1);
    }

    /**
     * applyToken hook uses a custom header instead of the standard Authorization: Bearer.
     */
    @Test
    public void testApplyTokenHookUsesCustomHeader() {
        stubTokenEndpoint("my-token", 3600);
        wireMockServer.stubFor(get(urlEqualTo(API_ENDPOINT))
                .withHeader("X-Auth-Token", equalTo("my-token"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        var script = """
                authentication {
                    rest {
                        oauth2 { oauth2Context ->
                            applyToken { request ->
                                request.header("X-Auth-Token", oauth2Context["access_token"])
                            }
                        }
                    }
                }
                """;

        createConnector(script).test();

        assertEquals(wireMockServer.findAll(getRequestedFor(urlEqualTo(API_ENDPOINT))
                .withHeader("X-Auth-Token", equalTo("my-token"))).size(), 1);
    }

    /**
     * Verifies that a custom value stored in the OAuth2 context during parseTokenResponse
     * is accessible in applyToken via the same context.
     */
    @Test
    public void testCustomContextValuePropagatedBetweenHooks() {
        wireMockServer.stubFor(post(urlEqualTo(TOKEN_ENDPOINT))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"tok\",\"expires_in\":3600,\"tenant_id\":\"acme-corp\"}")));
        wireMockServer.stubFor(get(urlEqualTo(API_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer tok"))
                .withHeader("X-Tenant-Id", equalTo("acme-corp"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"ok\"}")));

        var script = """
                authentication {
                    rest {
                        oauth2 { oauth2Context ->
                            parseTokenResponse { response ->
                                oauth2Context["access_token"] = response.access_token
                                oauth2Context["tenant_id"]    = response.tenant_id
                            }
                            applyToken { request ->
                                request.header("Authorization", "Bearer ${oauth2Context['access_token']}")
                                request.header("X-Tenant-Id",  oauth2Context["tenant_id"])
                            }
                        }
                    }
                }
                """;

        createConnector(script).test();

        assertEquals(wireMockServer.findAll(getRequestedFor(urlEqualTo(API_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer tok"))
                .withHeader("X-Tenant-Id", equalTo("acme-corp"))).size(), 1);
    }

    // --- helpers ---

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

    private void stubApiEndpoint(String expectedToken) {
        wireMockServer.stubFor(get(urlEqualTo(API_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer " + expectedToken))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"ok\"}")));
    }

    private AbstractGroovyRestConnector<BaseRestGroovyConnectorConfiguration> createConnector(String groovyScript) {
        var config = new OAuth2TestConfig(wireMockServer.port());
        var connector = new OAuth2ScriptConnector(groovyScript);
        connector.init(config);
        return connector;
    }

    // --- test doubles ---

    private static class OAuth2TestConfig extends BaseRestGroovyConnectorConfiguration
            implements RestClientConfiguration.OAuth2Authorization {

        private final int port;

        OAuth2TestConfig(int port) {
            this.port = port;
        }

        @Override public String getBaseAddress()          { return "http://localhost:" + port; }
        @Override public String getRestTestEndpoint()     { return API_ENDPOINT; }
        @Override public String getRestOAuth2TokenUrl()   { return "http://localhost:" + port + TOKEN_ENDPOINT; }
        @Override public String getRestOAuth2ClientId()   { return "test-client"; }
        @Override public GuardedString getRestOAuth2ClientSecret() {
            return new GuardedString("test-secret".toCharArray());
        }
        @Override public GuardedString getRestOAuth2PrivateKey() { return null; }
    }

    private static class OAuth2ScriptConnector
            extends AbstractGroovyRestConnector<BaseRestGroovyConnectorConfiguration> {

        private final String groovyScript;

        OAuth2ScriptConnector(String groovyScript) {
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
