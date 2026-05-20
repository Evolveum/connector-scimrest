/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.exception.auth.bearertoken;

import com.evolveum.polygon.scimrest.exception.WireMockTestSupport;
import com.evolveum.polygon.scimrest.exception.TestRestConnector;
import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import org.identityconnectors.common.security.GuardedString;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.*;

public class BearerTokenAuthTests extends WireMockTestSupport {

    private static final String API_ENDPOINT = "/api/resource";
    private static final String TOKEN_ENDPOINT = "/auth/token";

    @BeforeMethod
    public void setUp() {
        setUpWireMock();
    }

    @AfterMethod
    public void tearDown() {
        tearDownWireMock();
    }

    /**
     * Built-in bearer token sends Authorization: Bearer <value> without any Groovy script.
     */
    @Test
    public void testBuiltinBearerTokenSetsAuthorizationHeader() {
        wireMockServer.stubFor(get(urlEqualTo(API_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer my-bearer-token"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"ok\"}")));

        var config = new TestConfiguration(wireMockServer.port());
        config.testEndpoint = API_ENDPOINT;
        config.tokenValue = new GuardedString("my-bearer-token".toCharArray());

        var connector = new ScriptConnector(null);
        connector.init(config);
        connector.test();

        assertEquals(wireMockServer.findAll(getRequestedFor(urlEqualTo(API_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer my-bearer-token"))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 1);
    }

    @Test
    public void testInvalidTokenBecomes401() {
        wireMockServer.stubFor(get(urlEqualTo("/test"))
                .willReturn(aResponse().withStatus(401)));

        var config = new TestConfiguration(wireMockServer.port());
        config.testEndpoint = "/test";
        config.tokenValue = new GuardedString("invalid-token".toCharArray());

        TestRestConnector connector = new TestRestConnector(config);
        connector.init(config);

        try {
            connector.test();
            fail("Expected InvalidCredentialException was not thrown");
        } catch (Exception e) {
            assertTrue(e instanceof org.identityconnectors.framework.common.exceptions.InvalidCredentialException,
                    "Expected InvalidCredentialException but got: " + e.getClass().getName());
        }
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 1);
    }

    @Test
    public void testExpiredToken() {
        wireMockServer.stubFor(get(urlEqualTo("/test"))
                .willReturn(aResponse().withStatus(401)));

        var config = new TestConfiguration(wireMockServer.port());
        config.testEndpoint = "/test";
        config.tokenValue = new GuardedString("expired-token".toCharArray());

        TestRestConnector connector = new TestRestConnector(config);
        connector.init(config);

        try {
            connector.test();
            fail("Expected InvalidCredentialException was not thrown");
        } catch (Exception e) {
            assertTrue(e instanceof org.identityconnectors.framework.common.exceptions.InvalidCredentialException);
        }
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 1);
    }

    /**
     * execute() fetches a token and parseJson() extracts it from the response.
     */
    @Test
    public void testExecuteAndParseJson() {
        var tokenUrl = "http://localhost:" + wireMockServer.port() + TOKEN_ENDPOINT;

        wireMockServer.stubFor(get(urlEqualTo(TOKEN_ENDPOINT))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"fetched-token\"}")));
        wireMockServer.stubFor(get(urlEqualTo(API_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer fetched-token"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"ok\"}")));

        var script = """
                authentication {
                    rest {
                        bearerToken {
                            implementation {
                                if (!ctx["token"]) {
                                    def resp = execute(newRequest("%s"))
                                    ctx["token"] = parseJson(resp.body())["access_token"]
                                }
                                request.header("Authorization", "Bearer " + ctx["token"])
                            }
                        }
                    }
                }
                """.formatted(tokenUrl);

        var config = new TestConfiguration(wireMockServer.port());
        config.testEndpoint = API_ENDPOINT;
        var connector = new ScriptConnector(script);
        connector.init(config);
        connector.test();

        assertEquals(wireMockServer.findAll(getRequestedFor(urlEqualTo(API_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer fetched-token"))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 2);
    }

    /**
     * implementation block sets a static Bearer header.
     */
    @Test
    public void testStaticHeader() {
        wireMockServer.stubFor(get(urlEqualTo(API_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer static-token"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"ok\"}")));

        var script = """
                authentication {
                    rest {
                        bearerToken {
                            implementation {
                                request.header("Authorization", "Bearer static-token")
                            }
                        }
                    }
                }
                """;

        var config = new TestConfiguration(wireMockServer.port());
        config.testEndpoint = API_ENDPOINT;
        var connector = new ScriptConnector(script);
        connector.init(config);
        connector.test();

        assertEquals(wireMockServer.findAll(getRequestedFor(urlEqualTo(API_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer static-token"))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 1);
    }

    /**
     * decrypt() reads the token value from configuration.
     */
    @Test
    public void testDecryptToken() {
        wireMockServer.stubFor(get(urlEqualTo(API_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer my-secret-token"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"ok\"}")));

        var script = """
                authentication {
                    rest {
                        bearerToken {
                            implementation {
                                request.header("Authorization", "Bearer " + decrypt(configuration.restTokenValue))
                            }
                        }
                    }
                }
                """;

        var config = new TestConfiguration(wireMockServer.port());
        config.testEndpoint = API_ENDPOINT;
        config.tokenValue = new GuardedString("my-secret-token".toCharArray());
        var connector = new ScriptConnector(script);
        connector.init(config);
        connector.test();

        assertEquals(wireMockServer.findAll(getRequestedFor(urlEqualTo(API_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer my-secret-token"))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 1);
    }

    /**
     * Token stored in ctx is reused across requests without refetching.
     */
    @Test
    public void testTokenCaching() {
        wireMockServer.stubFor(get(urlEqualTo(API_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer cached-token"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"ok\"}")));

        var script = """
                authentication {
                    rest {
                        bearerToken {
                            implementation {
                                if (!ctx["token"]) {
                                    ctx["token"] = "cached-token"
                                }
                                request.header("Authorization", "Bearer " + ctx["token"])
                            }
                        }
                    }
                }
                """;

        var config = new TestConfiguration(wireMockServer.port());
        config.testEndpoint = API_ENDPOINT;
        var connector = new ScriptConnector(script);
        connector.init(config);
        connector.test();
        connector.test();

        assertEquals(wireMockServer.findAll(getRequestedFor(urlEqualTo(API_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer cached-token"))).size(), 2);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 2);
    }

    private static class TestConfiguration extends BaseTestConfiguration
            implements RestClientConfiguration.BearerTokenAuthorization {

        String testEndpoint;
        GuardedString tokenValue;

        TestConfiguration(int port) {
            super(port);
        }

        @Override
        public String getRestTestEndpoint() {
            return testEndpoint;
        }

        @Override
        public GuardedString getRestTokenValue() {
            return tokenValue;
        }
    }
}
