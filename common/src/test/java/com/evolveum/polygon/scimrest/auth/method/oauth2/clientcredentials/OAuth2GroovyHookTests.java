/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.auth.method.oauth2.clientcredentials;

import org.identityconnectors.common.security.GuardedString;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.*;

public class OAuth2GroovyHookTests extends AbstractOAuth2ClientCredentialsTests {

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
    public void testRefreshTokenFlowViaGroovyHooks() {
        stubTokenEndpoint(TOKEN_ENDPOINT, "new-access-token", 3600);
        stubRestApiEndpoint(API_ENDPOINT, "new-access-token");

        var script = """
                authentication {
                    rest {
                        oauth2ClientCredentials { oauth2Context ->
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
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 2);
    }

    @Test
    public void testBuildTokenRequestHookAddsCustomParameter() {
        stubTokenEndpointMatchingBody(TOKEN_ENDPOINT, "custom_param=extra-value", "hook-token", 3600);
        stubRestApiEndpoint(API_ENDPOINT, "hook-token");

        var script = """
                authentication {
                    rest {
                        oauth2ClientCredentials { oauth2Context ->
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
                        oauth2ClientCredentials { oauth2Context ->
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
                        oauth2ClientCredentials { oauth2Context ->
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
                        oauth2ClientCredentials { oauth2Context ->
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
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 2);
    }

    /**
     * Only parseTokenResponse hook overridden — buildTokenRequest and applyToken use defaults.
     * Verifies that the default applyToken (Authorization: Bearer) still works when only
     * token extraction is customized.
     */
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
                        oauth2ClientCredentials { oauth2Context ->
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

    /**
     * Only validateToken hook overridden — it always returns false, forcing token refetch
     * on every request. buildTokenRequest, parseTokenResponse, applyToken use defaults.
     */
    @Test
    public void testOnlyValidateTokenOverridden() {
        stubTokenEndpoint(TOKEN_ENDPOINT, "fresh-token", 3600);
        stubRestApiEndpoint(API_ENDPOINT, "fresh-token");

        var script = """
                authentication {
                    rest {
                        oauth2ClientCredentials { oauth2Context ->
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

    @Test
    public void testOnResponseHookCalledForAnyResponse() {
        stubTokenEndpoint(TOKEN_ENDPOINT, "test-token", 3600);
        stubRestApiEndpoint(API_ENDPOINT, "test-token");

        // Hook clears token after every 200 → forces re-fetch on next request
        var script = """
                authentication {
                    rest {
                        oauth2ClientCredentials { oauth2Context ->
                            onResponse { response ->
                                if (response.statusCode() == 200) {
                                    oauth2Context["access_token"] = null
                                }
                            }
                        }
                    }
                }
                """;

        var connector = createConnector(script);
        connector.test();
        connector.test();

        assertEquals(wireMockServer.findAll(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))).size(), 2,
                "Token re-fetched on second request — hook cleared it after first 200");
    }

    @Test
    public void testOnResponseHookSuppressesBuiltinRetry() {
        stubTokenEndpoint(TOKEN_ENDPOINT, "any-token", 3600);
        wireMockServer.stubFor(get(urlEqualTo(API_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer any-token"))
                .willReturn(aResponse().withStatus(401)));

        var script = """
                authentication {
                    rest {
                        oauth2ClientCredentials { oauth2Context ->
                            onResponse { response ->
                                // side-effect only — built-in 401 retry does not run
                            }
                        }
                    }
                }
                """;

        try {
            createConnector(script).test();
            fail("Expected InvalidCredentialException");
        } catch (org.identityconnectors.framework.common.exceptions.InvalidCredentialException e) {
            // expected
        }

        assertEquals(wireMockServer.findAll(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))).size(), 1,
                "Token fetched only once — hook suppressed built-in retry");
        assertEquals(wireMockServer.findAll(getRequestedFor(urlEqualTo(API_ENDPOINT))).size(), 1,
                "API called only once — no retry");
    }

    // --- helpers ---

    private OAuth2RestConnector createConnector(String groovyScript) {
        var config = new BaseClientCredentialsConfig(
                wireMockServer.port(), API_ENDPOINT,
                "http://localhost:" + wireMockServer.port() + TOKEN_ENDPOINT,
                "test-client", new GuardedString("test-secret".toCharArray()));
        var connector = new OAuth2RestConnector(groovyScript);
        connector.init(config);
        return connector;
    }
}
