/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.auth.method.oauth2.password;

import org.identityconnectors.common.security.GuardedString;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.*;

public class ScimOAuth2PasswordGroovyHookTests extends AbstractScimOAuth2PasswordTests {

    private static final String TOKEN_ENDPOINT = "/oauth/token";
    private static final GuardedString TEST_PASSWORD = new GuardedString("test-secret".toCharArray());

    @BeforeMethod
    public void setUp() {
        setUpWireMock();
    }

    @AfterMethod
    public void tearDown() {
        tearDownWireMock();
    }

    @Test
    public void testApplyTokenHookUsesCustomHeaderInScimRequests() {
        stubTokenEndpoint(TOKEN_ENDPOINT, "scim-pw-hook-token", 3600);
        stubScimEndpoints("scim-pw-hook-token");

        var script = """
                authentication {
                    scim {
                        oauth2Password { oauth2Context ->
                            applyToken { request ->
                                request.header("Authorization", "Bearer ${oauth2Context['access_token']}")
                            }
                        }
                    }
                }
                """;

        createScimConnector(TOKEN_ENDPOINT, "test-client", "alice", TEST_PASSWORD, script).schema();

        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(SCHEMAS_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer scim-pw-hook-token"))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 3);
    }

    @Test
    public void testBuildTokenRequestHookAddsCustomParameter() {
        stubTokenEndpointMatchingBody(TOKEN_ENDPOINT, "custom_param=scim-extra", "scim-custom-token", 3600);
        stubScimEndpoints("scim-custom-token");

        var script = """
                authentication {
                    scim {
                        oauth2Password { oauth2Context ->
                            buildTokenRequest { request ->
                                request.formParam("grant_type",   "password")
                                request.formParam("username",     "alice")
                                request.formParam("password",     decrypt(configuration.password))
                                request.formParam("custom_param", "scim-extra")
                            }
                        }
                    }
                }
                """;

        createScimConnector(TOKEN_ENDPOINT, "test-client", "alice", TEST_PASSWORD, script).schema();

        assertEquals(wireMockServer.findAll(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))
                .withRequestBody(containing("custom_param=scim-extra"))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 3);
    }

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
                        oauth2Password { oauth2Context ->
                            buildTokenRequest { request ->
                                request.formParam("grant_type", "password")
                                request.formParam("username",   "alice")
                                request.formParam("password",   "secret")
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

        createScimConnector(TOKEN_ENDPOINT, "test-client", "alice", TEST_PASSWORD, script).schema();

        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(SCHEMAS_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer scim-nested-token"))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 3);
    }
}
