/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.exception.auth.apikey;

import com.evolveum.polygon.scimrest.exception.WireMockTestSupport;
import com.evolveum.polygon.scimrest.exception.TestRestConnector;
import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import org.identityconnectors.common.security.GuardedString;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.*;

public class ApiKeyTests extends WireMockTestSupport {

    private static final String API_ENDPOINT = "/api/resource";

    @BeforeMethod
    public void setUp() {
        setUpWireMock();
    }

    @AfterMethod
    public void tearDown() {
        tearDownWireMock();
    }

    /**
     * Built-in API key sends X-API-Key header without any Groovy script.
     */
    @Test
    public void testBuiltinApiKeySetsHeader() {
        stubApiEndpoint(API_ENDPOINT, "X-API-Key", "my-api-key");

        var config = new TestConfiguration(wireMockServer.port());
        config.testEndpoint = API_ENDPOINT;
        config.keyName = "X-API-Key";
        config.keyLocation = "header";
        config.apiKey = new GuardedString("my-api-key".toCharArray());

        var connector = new ScriptConnector(null);
        connector.init(config);
        connector.test();

        assertEquals(wireMockServer.findAll(getRequestedFor(urlEqualTo(API_ENDPOINT))
                .withHeader("X-API-Key", equalTo("my-api-key"))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 1);
    }

    /**
     * Built-in API key sends key as query parameter when location is "query".
     */
    @Test
    public void testBuiltinApiKeyAsQueryParameter() {
        wireMockServer.stubFor(get(urlPathEqualTo(API_ENDPOINT))
                .withQueryParam("api_key", equalTo("my-query-key"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"ok\"}")));

        var config = new TestConfiguration(wireMockServer.port());
        config.testEndpoint = API_ENDPOINT;
        config.keyName = "api_key";
        config.keyLocation = "query";
        config.apiKey = new GuardedString("my-query-key".toCharArray());

        var connector = new ScriptConnector(null);
        connector.init(config);
        connector.test();

        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(API_ENDPOINT))
                .withQueryParam("api_key", equalTo("my-query-key"))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 1);
    }

    @Test
    public void testInvalidKeyBecomes403() {
        wireMockServer.stubFor(get(urlEqualTo("/test"))
                .willReturn(aResponse().withStatus(403)));

        var config = new TestConfiguration(wireMockServer.port());
        config.testEndpoint = "/test";
        config.keyName = "X-API-Key";
        config.keyLocation = "header";
        config.apiKey = new GuardedString("invalid-api-key".toCharArray());

        TestRestConnector connector = new TestRestConnector(config);
        connector.init(config);

        try {
            connector.test();
            fail("Expected InvalidCredentialException was not thrown");
        } catch (Exception e) {
            assertTrue(e instanceof org.identityconnectors.framework.common.exceptions.InvalidCredentialException,
                    "Expected InvalidCredentialException but got: " + e.getClass().getName());
            assertTrue(e.getMessage().contains("403"),
                    "Error message should contain status code 403");
        }
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 1);
    }

    /**
     * implementation block sets a static Authorization header.
     */
    @Test
    public void testStaticHeader() {
        stubApiEndpoint(API_ENDPOINT, "Authorization", "Bearer static-token");

        var script = """
                authentication {
                    rest {
                        apiKey {
                            implementation {
                                request.header("Authorization", "Bearer static-token")
                            }
                        }
                    }
                }
                """;

        var config = new TestConfiguration(wireMockServer.port());
        config.testEndpoint = API_ENDPOINT;
        config.keyName = "X-API-Key";
        config.keyLocation = "header";
        var connector = new ScriptConnector(script);
        connector.init(config);
        connector.test();

        assertEquals(wireMockServer.findAll(getRequestedFor(urlEqualTo(API_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer static-token"))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 1);
    }

    /**
     * Token stored in ctx is reused across requests without refetching.
     */
    @Test
    public void testTokenCaching() {
        stubApiEndpoint(API_ENDPOINT, "X-Token", "cached-token");

        var script = """
                authentication {
                    rest {
                        apiKey {
                            implementation {
                                if (!ctx["token"]) {
                                    ctx["token"] = "cached-token"
                                }
                                request.header("X-Token", ctx["token"])
                            }
                        }
                    }
                }
                """;

        var config = new TestConfiguration(wireMockServer.port());
        config.testEndpoint = API_ENDPOINT;
        config.keyName = "X-API-Key";
        config.keyLocation = "header";
        var connector = new ScriptConnector(script);
        connector.init(config);
        connector.test();
        connector.test();

        assertEquals(wireMockServer.findAll(getRequestedFor(urlEqualTo(API_ENDPOINT))
                .withHeader("X-Token", equalTo("cached-token"))).size(), 2);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 2);
    }

    /**
     * decrypt() decrypts a GuardedString from the configuration.
     */
    @Test
    public void testDecrypt() {
        stubApiEndpoint(API_ENDPOINT, "X-Api-Key", "my-secret");

        var script = """
                authentication {
                    rest {
                        apiKey {
                            implementation {
                                request.header("X-Api-Key", decrypt(configuration.restApiKey))
                            }
                        }
                    }
                }
                """;

        var config = new TestConfiguration(wireMockServer.port());
        config.testEndpoint = API_ENDPOINT;
        config.keyName = "X-Api-Key";
        config.keyLocation = "header";
        config.apiKey = new GuardedString("my-secret".toCharArray());
        var connector = new ScriptConnector(script);
        connector.init(config);
        connector.test();

        assertEquals(wireMockServer.findAll(getRequestedFor(urlEqualTo(API_ENDPOINT))
                .withHeader("X-Api-Key", equalTo("my-secret"))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 1);
    }

    /**
     * Multiple ctx values are all applied within the same implementation invocation.
     */
    @Test
    public void testMultipleStateValues() {
        wireMockServer.stubFor(get(urlEqualTo(API_ENDPOINT))
                .withHeader("X-Token", equalTo("tok"))
                .withHeader("X-Tenant", equalTo("acme"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"ok\"}")));

        var script = """
                authentication {
                    rest {
                        apiKey {
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

        var config = new TestConfiguration(wireMockServer.port());
        config.testEndpoint = API_ENDPOINT;
        config.keyName = "X-API-Key";
        config.keyLocation = "header";
        var connector = new ScriptConnector(script);
        connector.init(config);
        connector.test();

        assertEquals(wireMockServer.findAll(getRequestedFor(urlEqualTo(API_ENDPOINT))
                .withHeader("X-Token", equalTo("tok"))
                .withHeader("X-Tenant", equalTo("acme"))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 1);
    }

    // --- helpers ---

    private void stubApiEndpoint(String url, String headerName, String headerValue) {
        wireMockServer.stubFor(get(urlEqualTo(url))
                .withHeader(headerName, equalTo(headerValue))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"ok\"}")));
    }

    private static class TestConfiguration extends BaseTestConfiguration
            implements RestClientConfiguration.ApiKeyAuthorization {

        String testEndpoint;
        GuardedString apiKey;
        String keyName;
        String keyLocation;

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
            return keyName;
        }

        @Override
        public String getRestApiKeyLocation() {
            return keyLocation;
        }
    }
}
