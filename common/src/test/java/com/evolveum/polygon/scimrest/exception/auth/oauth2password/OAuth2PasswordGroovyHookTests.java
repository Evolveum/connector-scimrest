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

public class OAuth2PasswordGroovyHookTests extends AbstractOAuth2PasswordTests {

    private static final String TOKEN_ENDPOINT = "/oauth/token";
    private static final String API_ENDPOINT = "/api/verify";

    @BeforeMethod
    public void setUp() {
        setUpWireMock();
    }

    @AfterMethod
    public void tearDown() {
        tearDownWireMock();
    }

    @Test
    public void testBuildTokenRequestHookAddsCustomParameter() {
        stubTokenEndpointMatchingBody(TOKEN_ENDPOINT, "custom_param=extra-value", "hook-token", 3600);
        stubRestApiEndpoint(API_ENDPOINT, "hook-token");

        var script = """
                authentication {
                    rest {
                        oauth2Password { oauth2Context ->
                            buildTokenRequest { request ->
                                request.formParam("grant_type",   "password")
                                request.formParam("username",     configuration.username)
                                request.formParam("password",     decrypt(configuration.password))
                                request.formParam("custom_param", "extra-value")
                            }
                        }
                    }
                }
                """;

        createConnector(script).test();

        assertEquals(wireMockServer.findAll(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))
                .withRequestBody(containing("custom_param=extra-value"))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 2);
    }

    @Test
    public void testApplyTokenHookUsesCustomHeader() {
        stubTokenEndpoint(TOKEN_ENDPOINT, "my-token", 3600);
        wireMockServer.stubFor(get(urlEqualTo(API_ENDPOINT))
                .withHeader("X-Auth-Token", equalTo("my-token"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        var script = """
                authentication {
                    rest {
                        oauth2Password { oauth2Context ->
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
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 2);
    }

    @Test
    public void testParseTokenResponseHookHandlesCustomResponseStructure() {
        wireMockServer.stubFor(post(urlEqualTo(TOKEN_ENDPOINT))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"data\":{\"token\":\"nested-token\"},\"ok\":true}")));
        stubRestApiEndpoint(API_ENDPOINT, "nested-token");

        var script = """
                authentication {
                    rest {
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

        createConnector(script).test();

        assertEquals(wireMockServer.findAll(getRequestedFor(urlEqualTo(API_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer nested-token"))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 2);
    }

    @Test
    public void testOnlyValidateTokenOverridden() {
        stubTokenEndpoint(TOKEN_ENDPOINT, "fresh-token", 3600);
        stubRestApiEndpoint(API_ENDPOINT, "fresh-token");

        var script = """
                authentication {
                    rest {
                        oauth2Password { oauth2Context ->
                            validateToken {
                                false
                            }
                        }
                    }
                }
                """;

        var connector = createConnector(script);
        connector.test();
        connector.test();

        assertEquals(wireMockServer.findAll(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))).size(), 2);
        assertEquals(wireMockServer.findAll(getRequestedFor(urlEqualTo(API_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer fresh-token"))).size(), 2);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 4);
    }

    // --- helpers ---

    private OAuth2PasswordRestConnector createConnector(String groovyScript) {
        var config = new BasePasswordConfig(
                wireMockServer.port(), API_ENDPOINT,
                "http://localhost:" + wireMockServer.port() + TOKEN_ENDPOINT,
                "test-client", "alice", new GuardedString("secret".toCharArray()));
        var connector = new OAuth2PasswordRestConnector(groovyScript);
        connector.init(config);
        return connector;
    }
}
