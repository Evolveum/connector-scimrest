/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.exception.auth.oauth2saml;

import org.identityconnectors.common.security.GuardedString;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.*;

public class OAuth2SamlGroovyHookTests extends AbstractOAuth2SamlTests {

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
        stubTokenEndpointWithStatus(TOKEN_ENDPOINT, 200, "saml-hook-token");
        wireMockServer.stubFor(get(urlEqualTo(API_ENDPOINT))
                .withHeader("X-Auth-Token", equalTo("saml-hook-token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        var script = """
                authentication {
                    rest {
                        oauth2Saml { oauth2Context ->
                            applyToken { request ->
                                request.header("X-Auth-Token", oauth2Context["access_token"])
                            }
                        }
                    }
                }
                """;

        createConnector(script).test();

        assertEquals(wireMockServer.findAll(getRequestedFor(urlEqualTo(API_ENDPOINT))
                .withHeader("X-Auth-Token", equalTo("saml-hook-token"))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 2);
    }

    @Test
    public void testParseTokenResponseHookHandlesCustomResponseStructure() {
        wireMockServer.stubFor(post(urlEqualTo(TOKEN_ENDPOINT))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"data\":{\"token\":\"nested-saml-token\"},\"ok\":true}")));
        stubRestApiEndpoint(API_ENDPOINT, "nested-saml-token");

        var script = """
                authentication {
                    rest {
                        oauth2Saml { oauth2Context ->
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
                .withHeader("Authorization", equalTo("Bearer nested-saml-token"))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 2);
    }

    @Test
    public void testValidateTokenForcesRefetch() {
        stubTokenEndpointWithStatus(TOKEN_ENDPOINT, 200, "fresh-saml-token");
        stubRestApiEndpoint(API_ENDPOINT, "fresh-saml-token");

        var script = """
                authentication {
                    rest {
                        oauth2Saml { oauth2Context ->
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
                .withHeader("Authorization", equalTo("Bearer fresh-saml-token"))).size(), 2);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 4);
    }

    // --- helpers ---

    private OAuth2SamlRestConnector createConnector(String script) {
        var config = new BaseSamlConfig(
                wireMockServer.port(), API_ENDPOINT,
                "http://localhost:" + wireMockServer.port() + TOKEN_ENDPOINT,
                CLIENT_ID, new GuardedString(toPem(keyPair.getPrivate()).toCharArray()));
        var connector = new OAuth2SamlRestConnector(script);
        connector.init(config);
        return connector;
    }
}
