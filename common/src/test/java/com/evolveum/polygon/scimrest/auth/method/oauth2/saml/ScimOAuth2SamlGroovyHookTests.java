/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.auth.method.oauth2.saml;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.*;

public class ScimOAuth2SamlGroovyHookTests extends AbstractScimOAuth2SamlTests {

    private static final String TOKEN_ENDPOINT = "/oauth/token";
    private static final String CLIENT_ID = "test-scim-client";

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
    public void testApplyTokenHookUsesCustomHeaderInScimRequests() {
        stubTokenEndpointWithStatus(TOKEN_ENDPOINT, 200, "scim-saml-hook-token");
        wireMockServer.stubFor(get(urlPathEqualTo(SCHEMAS_ENDPOINT))
                .withHeader("X-Auth-Token", equalTo("scim-saml-hook-token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/scim+json")
                        .withBody(EMPTY_LIST_RESPONSE)));
        wireMockServer.stubFor(get(urlPathEqualTo(RESOURCES_ENDPOINT))
                .withHeader("X-Auth-Token", equalTo("scim-saml-hook-token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/scim+json")
                        .withBody(EMPTY_LIST_RESPONSE)));

        var script = """
                authentication {
                    scim {
                        oauth2Saml { oauth2Context ->
                            applyToken { request ->
                                request.header("X-Auth-Token", oauth2Context["access_token"])
                            }
                        }
                    }
                }
                """;

        createScimConnector(keyPair.getPrivate(), TOKEN_ENDPOINT, CLIENT_ID, script).schema();

        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(SCHEMAS_ENDPOINT))
                .withHeader("X-Auth-Token", equalTo("scim-saml-hook-token"))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 3);
    }

    @Test
    public void testParseTokenResponseHookHandlesCustomResponseStructure() {
        wireMockServer.stubFor(post(urlEqualTo(TOKEN_ENDPOINT))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"data\":{\"token\":\"scim-nested-saml-token\"},\"ok\":true}")));
        stubScimEndpoints("scim-nested-saml-token");

        var script = """
                authentication {
                    scim {
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

        createScimConnector(keyPair.getPrivate(), TOKEN_ENDPOINT, CLIENT_ID, script).schema();

        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(SCHEMAS_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer scim-nested-saml-token"))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 3);
    }

    @Test
    public void testValidateTokenForcesRefetch() {
        stubTokenEndpointWithStatus(TOKEN_ENDPOINT, 200, "fresh-scim-saml-token");
        stubScimEndpoints("fresh-scim-saml-token");

        var script = """
                authentication {
                    scim {
                        oauth2Saml { oauth2Context ->
                            validateToken {
                                false
                            }
                        }
                    }
                }
                """;

        var connector = createScimConnector(keyPair.getPrivate(), TOKEN_ENDPOINT, CLIENT_ID, script);
        connector.schema();
        connector.schema();

        assertEquals(wireMockServer.findAll(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))).size(), 2);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 4);
    }
}
