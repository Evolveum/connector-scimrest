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
 * Automated tests for OAuth2 Groovy hook customization in the SCIM context.
 *
 * Covers all four hook points: validateToken, buildTokenRequest, parseTokenResponse, applyToken.
 * Verification is done via SCIM schema discovery requests (Authorization header on /Schemas).
 */
public class ScimOAuth2GroovyHookTests extends AbstractScimOAuth2Tests {

    private static final String TOKEN_ENDPOINT = "/oauth/token";
    private static final GuardedString TEST_SECRET = new GuardedString("test-secret".toCharArray());

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
        stubTokenEndpoint(TOKEN_ENDPOINT, "scim-hook-token", 3600);
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

        createScimConnector(TOKEN_ENDPOINT, "test-client", TEST_SECRET, script).schema();

        assertTrue(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(SCHEMAS_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer scim-hook-token"))).size() >= 1);
    }

    /**
     * buildTokenRequest hook adds a custom form parameter to the token request.
     */
    @Test
    public void testBuildTokenRequestHookAddsCustomParameter() {
        stubTokenEndpointMatchingBody(TOKEN_ENDPOINT, "custom_param=scim-extra", "scim-custom-token", 3600);
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

        createScimConnector(TOKEN_ENDPOINT, "test-client", TEST_SECRET, script).schema();

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

        createScimConnector(TOKEN_ENDPOINT, "test-client", TEST_SECRET, script).schema();

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

        createScimConnector(TOKEN_ENDPOINT, "test-client", TEST_SECRET, script).schema();

        assertEquals(wireMockServer.findAll(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))
                .withRequestBody(containing("grant_type=refresh_token"))
                .withRequestBody(containing("refresh_token=mocked-refresh-token"))).size(), 1);
        assertTrue(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(SCHEMAS_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer scim-refreshed-token"))).size() >= 1);
    }
}
