/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.exception.auth.bearertoken;

import com.evolveum.polygon.scimrest.config.ScimClientConfiguration;
import com.evolveum.polygon.scimrest.exception.WireMockTestSupport;
import com.evolveum.polygon.scimrest.groovy.AbstractGroovyRestConnector;
import com.evolveum.polygon.scimrest.groovy.BaseGroovyConnectorConfiguration;
import com.evolveum.polygon.scimrest.groovy.BaseRestGroovyConnectorConfiguration;
import org.identityconnectors.common.security.GuardedString;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.*;

public class ScimBearerTokenAuthTests extends WireMockTestSupport {

    private static final String SCIM_BASE_PATH = "/scim";
    private static final String SCHEMAS_ENDPOINT = SCIM_BASE_PATH + "/Schemas";
    private static final String RESOURCES_ENDPOINT = SCIM_BASE_PATH + "/ResourceTypes";
    private static final String TOKEN_ENDPOINT = "/auth/token";

    private static final String EMPTY_LIST_RESPONSE = """
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
     * Built-in SCIM bearer token sends Authorization: Bearer <value> without any Groovy script.
     */
    @Test
    public void testBuiltinBearerTokenSetsAuthorizationHeader() {
        stubScimEndpoints("Bearer my-scim-token");

        var config = new ScimTestConfiguration(wireMockServer.port());
        config.tokenValue = new GuardedString("my-scim-token".toCharArray());

        var connector = new ScriptConnector(null);
        connector.init(config);
        connector.schema();

        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(SCHEMAS_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer my-scim-token"))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 2);
    }

    /**
     * implementation block sets a static Authorization header on SCIM requests.
     */
    @Test
    public void testStaticHeader() {
        stubScimEndpoints("Bearer static-token");

        var script = """
                authentication {
                    scim {
                        bearer {
                            implementation {
                                request.header("Authorization", "Bearer static-token")
                            }
                        }
                    }
                }
                """;

        createScimConnector(script).schema();

        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(SCHEMAS_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer static-token"))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 2);
    }

    /**
     * Multiple ctx values are applied to every SCIM request.
     */
    @Test
    public void testMultipleStateValues() {
        wireMockServer.stubFor(get(urlPathEqualTo(SCHEMAS_ENDPOINT))
                .withHeader("X-Token", equalTo("tok"))
                .withHeader("X-Tenant", equalTo("acme"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/scim+json")
                        .withBody(EMPTY_LIST_RESPONSE)));
        wireMockServer.stubFor(get(urlPathEqualTo(RESOURCES_ENDPOINT))
                .withHeader("X-Token", equalTo("tok"))
                .withHeader("X-Tenant", equalTo("acme"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/scim+json")
                        .withBody(EMPTY_LIST_RESPONSE)));

        var script = """
                authentication {
                    scim {
                        bearer {
                            implementation {
                                if (!ctx["token"]) {
                                    ctx["token"]  = "tok"
                                    ctx["tenant"] = "acme"
                                }
                                request.header("X-Token",  ctx["token"])
                                request.header("X-Tenant", ctx["tenant"])
                            }
                        }
                    }
                }
                """;

        createScimConnector(script).schema();

        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(SCHEMAS_ENDPOINT))
                .withHeader("X-Token", equalTo("tok"))
                .withHeader("X-Tenant", equalTo("acme"))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 2);
    }

    /**
     * execute() fetches a token from a stub endpoint and parseJson() extracts it.
     */
    @Test
    public void testExecuteAndParseJson() {
        var tokenUrl = "http://localhost:" + wireMockServer.port() + TOKEN_ENDPOINT;

        wireMockServer.stubFor(get(urlEqualTo(TOKEN_ENDPOINT))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"fetched-token\"}")));
        stubScimEndpoints("Bearer fetched-token");

        var script = """
                authentication {
                    scim {
                        bearer {
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

        createScimConnector(script).schema();

        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(SCHEMAS_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer fetched-token"))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 3);
    }

    /**
     * decrypt() decrypts a GuardedString from the configuration.
     */
    @Test
    public void testDecrypt() {
        stubScimEndpoints("Bearer my-secret");

        var script = """
                authentication {
                    scim {
                        bearer {
                            implementation {
                                request.header("Authorization", "Bearer " + decrypt(configuration.scimTokenValue))
                            }
                        }
                    }
                }
                """;

        createScimConnectorWithToken(script, new GuardedString("my-secret".toCharArray())).schema();

        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(SCHEMAS_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer my-secret"))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 2);
    }

    /**
     * Built-in SCIM bearer token sends Authorization: Bearer <value> to the REST test endpoint.
     */
    @Test
    public void testBuiltinBearerTokenSentToTestEndpoint() {
        String testEndpoint = "/health";
        stubScimEndpoints("Bearer endpoint-bearer-token");
        wireMockServer.stubFor(get(urlEqualTo(SCIM_BASE_PATH + testEndpoint))
                .willReturn(aResponse().withStatus(200)));

        var config = new ScimBearerRestConfig(wireMockServer.port(), "endpoint-bearer-token");
        config.setRestTestEndpoint(testEndpoint);
        var connector = new ScriptConnector(null);
        connector.init(config);
        connector.test();

        assertEquals(wireMockServer.findAll(getRequestedFor(urlEqualTo(SCIM_BASE_PATH + testEndpoint))
                .withHeader("Authorization", equalTo("Bearer endpoint-bearer-token"))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 3);
    }

    /**
     * Groovy implementation block must override the built-in SCIM bearer even when scimTokenValue is configured.
     */
    @Test
    public void testGroovyImplementationOverridesBuiltinWhenTokenValueConfigured() {
        stubScimEndpoints("Bearer groovy-scim-token");

        var script = """
                authentication {
                    scim {
                        bearer {
                            implementation {
                                request.header("Authorization", "Bearer groovy-scim-token")
                            }
                        }
                    }
                }
                """;

        createScimConnectorWithToken(script, new GuardedString("builtin-token".toCharArray())).schema();

        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(SCHEMAS_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer groovy-scim-token"))).size(), 1,
                "Groovy implementation block should override built-in SCIM bearer auth");
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 2);
    }

    // --- helpers ---

    private void stubScimEndpoints(String authorizationHeader) {
        wireMockServer.stubFor(get(urlPathEqualTo(SCHEMAS_ENDPOINT))
                .withHeader("Authorization", equalTo(authorizationHeader))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/scim+json")
                        .withBody(EMPTY_LIST_RESPONSE)));
        wireMockServer.stubFor(get(urlPathEqualTo(RESOURCES_ENDPOINT))
                .withHeader("Authorization", equalTo(authorizationHeader))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/scim+json")
                        .withBody(EMPTY_LIST_RESPONSE)));
    }

    private AbstractGroovyRestConnector<?> createScimConnector(String script) {
        var config = new ScimTestConfiguration(wireMockServer.port());
        var connector = new ScriptConnector(script);
        connector.init(config);
        return connector;
    }

    private AbstractGroovyRestConnector<?> createScimConnectorWithToken(String script, GuardedString token) {
        var config = new ScimTestConfiguration(wireMockServer.port());
        config.tokenValue = token;
        var connector = new ScriptConnector(script);
        connector.init(config);
        return connector;
    }

    private class ScimBearerRestConfig extends BaseRestGroovyConnectorConfiguration
            implements ScimClientConfiguration.BearerTokenAuthorization {

        private final int port;
        private final GuardedString token;

        ScimBearerRestConfig(int port, String token) {
            this.port = port;
            this.token = new GuardedString(token.toCharArray());
            setBaseAddress("http://localhost:" + port);
        }

        @Override
        public String getScimBaseUrl() {
            return "http://localhost:" + port + SCIM_BASE_PATH;
        }

        @Override
        public GuardedString getScimTokenValue() {
            return token;
        }
    }

    private class ScimTestConfiguration extends BaseGroovyConnectorConfiguration
            implements ScimClientConfiguration.BearerTokenAuthorization {

        private final int port;
        GuardedString tokenValue;

        ScimTestConfiguration(int port) {
            this.port = port;
        }

        @Override
        public String getScimBaseUrl() {
            return "http://localhost:" + port + SCIM_BASE_PATH;
        }

        @Override
        public GuardedString getScimTokenValue() {
            return tokenValue;
        }
    }
}
