/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.exception.auth.preference;

import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.exception.WireMockTestSupport;
import org.identityconnectors.common.security.GuardedString;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.*;

/**
 * Tests for the auth preference mechanism — probe-based auth method selection.
 * <p>
 * When preference is defined, test() IS the probe — it tries each method in order,
 * first 200 wins, and no additional request is sent afterwards.
 */
public class AuthPreferenceTests extends WireMockTestSupport {

    private static final String TEST_ENDPOINT = "/test";

    @BeforeMethod
    public void setUp() {
        setUpWireMock();
    }

    @AfterMethod
    public void tearDown() {
        tearDownWireMock();
    }

    /**
     * preference picks the primary method when its probe returns 200 — exactly 1 request sent.
     */
    @Test
    public void testPreferencePrimarySelected() {
        wireMockServer.stubFor(get(urlEqualTo(TEST_ENDPOINT))
                .withHeader("X-Api-Key", equalTo("primary-key"))
                .willReturn(aResponse().withStatus(200)));

        var script = """
                authentication {
                    rest {
                        apiKey {
                            implementation {
                                request.header("X-Api-Key", "primary-key")
                            }
                        }
                        bearer {
                            implementation {
                                request.header("Authorization", "Bearer fallback-token")
                            }
                        }
                        preference apiKey, bearer
                    }
                }
                """;

        var config = new TestConfiguration(wireMockServer.port());
        config.testEndpoint = TEST_ENDPOINT;
        var connector = new ScriptConnector(script);
        connector.init(config);
        connector.test();

        // probe with primary succeeded → test() done, exactly 1 request
        assertEquals(wireMockServer.findAll(
                getRequestedFor(urlEqualTo(TEST_ENDPOINT))
                        .withHeader("X-Api-Key", equalTo("primary-key"))).size(), 1);
        // fallback was never tried
        assertEquals(wireMockServer.findAll(
                getRequestedFor(urlEqualTo(TEST_ENDPOINT))
                        .withHeader("Authorization", equalTo("Bearer fallback-token"))).size(), 0);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 1);
    }

    /**
     * preference skips primary (401) and selects fallback — probe IS test, no extra request.
     */
    @Test
    public void testPreferenceFallbackSelected() {
        wireMockServer.stubFor(get(urlEqualTo(TEST_ENDPOINT))
                .withHeader("X-Api-Key", equalTo("bad-key"))
                .willReturn(aResponse().withStatus(401)));
        wireMockServer.stubFor(get(urlEqualTo(TEST_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer fallback-token"))
                .willReturn(aResponse().withStatus(200)));

        var script = """
                authentication {
                    rest {
                        apiKey {
                            implementation {
                                request.header("X-Api-Key", "bad-key")
                            }
                        }
                        bearer {
                            implementation {
                                request.header("Authorization", "Bearer fallback-token")
                            }
                        }
                        preference apiKey, bearer
                    }
                }
                """;

        var config = new TestConfiguration(wireMockServer.port());
        config.testEndpoint = TEST_ENDPOINT;
        var connector = new ScriptConnector(script);
        connector.init(config);
        connector.test();

        // primary probe happened and failed
        assertEquals(wireMockServer.findAll(
                getRequestedFor(urlEqualTo(TEST_ENDPOINT))
                        .withHeader("X-Api-Key", equalTo("bad-key"))).size(), 1);
        // fallback probe succeeded → test() done, exactly 1 request (no duplicate)
        assertEquals(wireMockServer.findAll(
                getRequestedFor(urlEqualTo(TEST_ENDPOINT))
                        .withHeader("Authorization", equalTo("Bearer fallback-token"))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 2);
    }

    /**
     * Without test endpoint, preference uses first method without probing — 0 requests.
     */
    @Test
    public void testPreferenceNoTestEndpointUsesFirst() {
        var script = """
                authentication {
                    rest {
                        apiKey {
                            implementation {
                                request.header("X-Api-Key", "primary-key")
                            }
                        }
                        bearer {
                            implementation {
                                request.header("Authorization", "Bearer fallback-token")
                            }
                        }
                        preference apiKey, bearer
                    }
                }
                """;

        var config = new TestConfiguration(wireMockServer.port());
        // intentionally no setRestTestEndpoint()
        var connector = new ScriptConnector(script);
        connector.init(config);
        connector.test();

        // no probe and no test request — nothing to validate connectivity against
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 0);
    }

    /**
     * Without preference, first-match applies — exactly 1 test request with primary auth.
     */
    @Test
    public void testNoPreferenceUsesFirstMethod() {
        wireMockServer.stubFor(get(urlEqualTo(TEST_ENDPOINT))
                .withHeader("X-Api-Key", equalTo("primary-key"))
                .willReturn(aResponse().withStatus(200)));

        var script = """
                authentication {
                    rest {
                        apiKey {
                            implementation {
                                request.header("X-Api-Key", "primary-key")
                            }
                        }
                        bearer {
                            implementation {
                                request.header("Authorization", "Bearer fallback-token")
                            }
                        }
                        // no preference — first-match
                    }
                }
                """;

        var config = new TestConfiguration(wireMockServer.port());
        config.testEndpoint = TEST_ENDPOINT;
        var connector = new ScriptConnector(script);
        connector.init(config);
        connector.test();

        assertEquals(wireMockServer.findAll(
                getRequestedFor(urlEqualTo(TEST_ENDPOINT))
                        .withHeader("X-Api-Key", equalTo("primary-key"))).size(), 1);
        assertEquals(wireMockServer.findAll(
                getRequestedFor(urlEqualTo(TEST_ENDPOINT))
                        .withHeader("Authorization", equalTo("Bearer fallback-token"))).size(), 0);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 1);
    }

    /**
     * Last registration of the same type overwrites the previous — only the new impl is used.
     */
    @Test
    public void testMethodOverwrite() {
        wireMockServer.stubFor(get(urlEqualTo(TEST_ENDPOINT))
                .withHeader("X-Api-Key", equalTo("v2-key"))
                .willReturn(aResponse().withStatus(200)));

        var script = """
                authentication {
                    rest {
                        apiKey {
                            implementation {
                                request.header("X-Api-Key", "v1-key")
                            }
                        }
                        apiKey {
                            implementation {
                                request.header("X-Api-Key", "v2-key")
                            }
                        }
                        preference apiKey
                    }
                }
                """;

        var config = new TestConfiguration(wireMockServer.port());
        config.testEndpoint = TEST_ENDPOINT;
        var connector = new ScriptConnector(script);
        connector.init(config);
        connector.test();

        // v2 probe succeeded, exactly 1 request
        assertEquals(wireMockServer.findAll(
                getRequestedFor(urlEqualTo(TEST_ENDPOINT))
                        .withHeader("X-Api-Key", equalTo("v2-key"))).size(), 1);
        assertEquals(wireMockServer.findAll(
                getRequestedFor(urlEqualTo(TEST_ENDPOINT))
                        .withHeader("X-Api-Key", equalTo("v1-key"))).size(), 0);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 1);
    }

    private static class TestConfiguration extends BaseTestConfiguration
            implements RestClientConfiguration.ApiKeyAuthorization,
            RestClientConfiguration.BearerTokenAuthorization {

        String testEndpoint;
        GuardedString apiKey;
        String apiKeyName;
        String apiKeyLocation;
        GuardedString tokenValue;

        TestConfiguration(int port) {
            super(port);
        }

        @Override
        public String getRestTestEndpoint() {
            return testEndpoint;
        }

        @Override
        public GuardedString getRestApiKey() {
            return apiKey;
        }

        @Override
        public String getRestApiKeyName() {
            return apiKeyName;
        }

        @Override
        public String getRestApiKeyLocation() {
            return apiKeyLocation;
        }

        @Override
        public GuardedString getRestTokenValue() {
            return tokenValue;
        }
    }
}
