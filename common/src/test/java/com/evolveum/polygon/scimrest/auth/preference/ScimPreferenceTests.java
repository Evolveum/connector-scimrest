/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.auth.preference;

import com.evolveum.polygon.scimrest.config.ScimClientConfiguration;
import com.evolveum.polygon.scimrest.support.WireMockTestSupport;
import org.identityconnectors.common.security.GuardedString;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.*;

/**
 * Tests for the auth preference mechanism on the SCIM channel.
 * <p>
 * Probe always targets getScimBaseUrl() + "/Schemas" — there is no separate configurable
 * test endpoint for SCIM. The same endpoint is also used for schema discovery, so the probe
 * and the first schema-discovery request hit the same WireMock stub.
 * <p>
 * Tests are driven via connector.schema() which triggers SCIM initialization and therefore
 * the probe.
 */
public class ScimPreferenceTests extends WireMockTestSupport {

    private static final String SCIM_BASE = "/scim";
    private static final String SCHEMAS_PATH = SCIM_BASE + "/Schemas";
    private static final String RESOURCES_PATH = SCIM_BASE + "/ResourceTypes";

    private static final String PRIMARY_HEADER = "Basic cHJpbWFyeTpwYXNz";
    private static final String FALLBACK_HEADER = "Bearer fallback-token";

    private static final String EMPTY_LIST = """
            {"schemas":["urn:ietf:params:scim:api:messages:2.0:ListResponse"],"totalResults":0,"Resources":[]}
            """;

    @BeforeMethod
    public void setUp() {
        setUpWireMock();
    }

    @AfterMethod
    public void tearDown() {
        tearDownWireMock();
    }

    /**
     * preference picks the primary method when its probe returns 200.
     */
    @Test
    public void testPreferencePrimarySelected() {
        stubScimEndpoints("Authorization", PRIMARY_HEADER);

        var script = """
                authentication {
                    scim {
                        basic {
                            implementation {
                                request.header("Authorization", "Basic cHJpbWFyeTpwYXNz")
                            }
                        }
                        bearer {
                            implementation {
                                request.header("Authorization", "Bearer fallback-token")
                            }
                        }
                        preference basic, bearer
                    }
                }
                """;

        var config = new TestConfiguration(wireMockServer.port());
        var connector = new ScriptConnector(script);
        connector.init(config);
        connector.schema();

        assertEquals(wireMockServer.findAll(
                getRequestedFor(urlPathEqualTo(SCHEMAS_PATH))
                        .withHeader("Authorization", equalTo(PRIMARY_HEADER))).size(), 2);
        assertEquals(wireMockServer.findAll(
                getRequestedFor(urlPathEqualTo(SCHEMAS_PATH))
                        .withHeader("Authorization", equalTo(FALLBACK_HEADER))).size(), 0);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 3);
    }

    /**
     * preference skips primary (401) and selects fallback.
     */
    @Test
    public void testPreferenceFallbackSelected() {
        wireMockServer.stubFor(get(urlPathEqualTo(SCHEMAS_PATH))
                .withHeader("Authorization", equalTo(PRIMARY_HEADER))
                .willReturn(aResponse().withStatus(401)));
        stubScimEndpoints("Authorization", FALLBACK_HEADER);

        var script = """
                authentication {
                    scim {
                        basic {
                            implementation {
                                request.header("Authorization", "Basic cHJpbWFyeTpwYXNz")
                            }
                        }
                        bearer {
                            implementation {
                                request.header("Authorization", "Bearer fallback-token")
                            }
                        }
                        preference basic, bearer
                    }
                }
                """;

        var config = new TestConfiguration(wireMockServer.port());
        var connector = new ScriptConnector(script);
        connector.init(config);
        connector.schema();

        assertEquals(wireMockServer.findAll(
                getRequestedFor(urlPathEqualTo(SCHEMAS_PATH))
                        .withHeader("Authorization", equalTo(PRIMARY_HEADER))).size(), 1);
        assertEquals(wireMockServer.findAll(
                getRequestedFor(urlPathEqualTo(SCHEMAS_PATH))
                        .withHeader("Authorization", equalTo(FALLBACK_HEADER))).size(), 2);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 4);
    }

    /**
     * Without preference, first method is used without probing.
     */
    @Test
    public void testNoPreferenceUsesFirstMethod() {
        stubScimEndpoints("Authorization", PRIMARY_HEADER);

        var script = """
                authentication {
                    scim {
                        basic {
                            implementation {
                                request.header("Authorization", "Basic cHJpbWFyeTpwYXNz")
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
        var connector = new ScriptConnector(script);
        connector.init(config);
        connector.schema();

        assertEquals(wireMockServer.findAll(
                getRequestedFor(urlPathEqualTo(SCHEMAS_PATH))
                        .withHeader("Authorization", equalTo(PRIMARY_HEADER))).size(), 1);
        assertEquals(wireMockServer.findAll(
                getRequestedFor(urlPathEqualTo(SCHEMAS_PATH))
                        .withHeader("Authorization", equalTo(FALLBACK_HEADER))).size(), 0);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 2);
    }

    /**
     * Last registration of the same type overwrites the previous.
     */
    @Test
    public void testMethodOverwrite() {
        String v1Header = "Basic djEta2V5OnBhc3M=";
        String v2Header = "Basic djIta2V5OnBhc3M=";
        stubScimEndpoints("Authorization", v2Header);

        var script = """
                authentication {
                    scim {
                        basic {
                            implementation {
                                request.header("Authorization", "Basic djEta2V5OnBhc3M=")
                            }
                        }
                        basic {
                            implementation {
                                request.header("Authorization", "Basic djIta2V5OnBhc3M=")
                            }
                        }
                        preference basic
                    }
                }
                """;

        var config = new TestConfiguration(wireMockServer.port());
        var connector = new ScriptConnector(script);
        connector.init(config);
        connector.schema();

        assertEquals(wireMockServer.findAll(
                getRequestedFor(urlPathEqualTo(SCHEMAS_PATH))
                        .withHeader("Authorization", equalTo(v2Header))).size(), 2);
        assertEquals(wireMockServer.findAll(
                getRequestedFor(urlPathEqualTo(SCHEMAS_PATH))
                        .withHeader("Authorization", equalTo(v1Header))).size(), 0);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 3);
    }

    // --- helpers ---

    private void stubScimEndpoints(String headerName, String headerValue) {
        wireMockServer.stubFor(get(urlPathEqualTo(SCHEMAS_PATH))
                .withHeader(headerName, equalTo(headerValue))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/scim+json")
                        .withBody(EMPTY_LIST)));
        wireMockServer.stubFor(get(urlPathEqualTo(RESOURCES_PATH))
                .withHeader(headerName, equalTo(headerValue))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/scim+json")
                        .withBody(EMPTY_LIST)));
    }

    private static class TestConfiguration extends BaseTestConfiguration
            implements ScimClientConfiguration.BasicAuthorization,
            ScimClientConfiguration.BearerTokenAuthorization {

        private final int port;

        TestConfiguration(int port) {
            super(port);
            this.port = port;
        }

        @Override
        public String getScimBaseUrl() {
            return "http://localhost:" + port + SCIM_BASE;
        }

        @Override
        public String getScimUsername() {
            return null;
        }

        @Override
        public GuardedString getScimPassword() {
            return null;
        }

        @Override
        public GuardedString getScimTokenValue() {
            return null;
        }
    }
}
