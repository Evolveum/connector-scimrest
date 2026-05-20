/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.exception.auth.oauth2jwtbearer;

import org.identityconnectors.common.security.GuardedString;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.*;

public class OAuth2JwtBearerGroovyHookTests extends AbstractOAuth2JwtBearerTests {

    private static final String TOKEN_ENDPOINT = "/oauth2/token";
    private static final String API_ENDPOINT = "/api/resource";
    private static final String CLIENT_ID = "test-client";

    private KeyPair keyPair;

    @BeforeMethod
    public void setUp() throws Exception {
        setUpWireMock();
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keyPair = gen.generateKeyPair();
    }

    @AfterMethod
    public void tearDown() {
        tearDownWireMock();
    }

    @Test
    public void testApplyTokenHookUsesCustomHeader() {
        stubTokenEndpointWithStatus(TOKEN_ENDPOINT, 200, "jwt-bearer-token");
        wireMockServer.stubFor(get(urlEqualTo(API_ENDPOINT))
                .withHeader("X-Auth-Token", equalTo("jwt-bearer-token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        var script = """
                authentication {
                    rest {
                        oauth2JwtBearer { oauth2Context ->
                            applyToken { request ->
                                request.header("X-Auth-Token", oauth2Context["access_token"])
                            }
                        }
                    }
                }
                """;

        createConnector(script).test();

        assertEquals(wireMockServer.findAll(getRequestedFor(urlEqualTo(API_ENDPOINT))
                .withHeader("X-Auth-Token", equalTo("jwt-bearer-token"))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 2);
    }

    @Test
    public void testParseTokenResponseHookHandlesCustomResponseStructure() {
        wireMockServer.stubFor(post(urlEqualTo(TOKEN_ENDPOINT))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"data\":{\"token\":\"nested-jwt-token\"},\"ok\":true}")));
        stubRestApiEndpoint(API_ENDPOINT, "nested-jwt-token");

        var script = """
                authentication {
                    rest {
                        oauth2JwtBearer { oauth2Context ->
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
                .withHeader("Authorization", equalTo("Bearer nested-jwt-token"))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 2);
    }

    @Test
    public void testOnlyParseTokenResponseOverridden() {
        wireMockServer.stubFor(post(urlEqualTo(TOKEN_ENDPOINT))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"token\":\"custom-field-token\",\"expires_in\":3600}")));
        stubRestApiEndpoint(API_ENDPOINT, "custom-field-token");

        var script = """
                authentication {
                    rest {
                        oauth2JwtBearer { oauth2Context ->
                            parseTokenResponse { response ->
                                oauth2Context["access_token"] = response.token
                            }
                        }
                    }
                }
                """;

        createConnector(script).test();

        assertEquals(wireMockServer.findAll(getRequestedFor(urlEqualTo(API_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer custom-field-token"))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 2);
    }

    @Test
    public void testValidateTokenForcesRefetch() {
        stubTokenEndpointWithStatus(TOKEN_ENDPOINT, 200, "fresh-jwt-token");
        stubRestApiEndpoint(API_ENDPOINT, "fresh-jwt-token");

        var script = """
                authentication {
                    rest {
                        oauth2JwtBearer { oauth2Context ->
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
                .withHeader("Authorization", equalTo("Bearer fresh-jwt-token"))).size(), 2);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 4);
    }

    // --- helpers ---

    private OAuth2RestConnector createConnector(String script) {
        var config = new BaseJwtBearerConfig(
                wireMockServer.port(), API_ENDPOINT,
                "http://localhost:" + wireMockServer.port() + TOKEN_ENDPOINT,
                CLIENT_ID, new GuardedString(toPem(keyPair.getPrivate()).toCharArray()));
        var connector = new OAuth2RestConnector(script);
        connector.init(config);
        return connector;
    }
}
