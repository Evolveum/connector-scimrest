/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.auth.method.basic;

import com.evolveum.polygon.scimrest.support.WireMockTestSupport;
import com.evolveum.polygon.scimrest.support.TestRestConnector;
import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import org.identityconnectors.common.security.GuardedString;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Base64;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.*;

public class BasicAuthTests extends WireMockTestSupport {

    private static final String API_ENDPOINT = "/api/resource";

    @BeforeMethod
    public void setUp() {
        setUpWireMock();
    }

    @AfterMethod
    public void tearDown() {
        tearDownWireMock();
    }

    @Test
    public void testInvalidCredentialsBecomes401() {
        wireMockServer.stubFor(get(urlEqualTo("/test"))
                .willReturn(aResponse().withStatus(401)));

        var config = new TestConfiguration(wireMockServer.port());
        config.testEndpoint = "/test";
        config.username = "wronguser";
        config.password = new GuardedString("wrongpass".toCharArray());

        TestRestConnector connector = new TestRestConnector(config);
        connector.init(config);

        try {
            connector.test();
            fail("Expected InvalidCredentialException was not thrown");
        } catch (Exception e) {
            assertTrue(e instanceof org.identityconnectors.framework.common.exceptions.InvalidCredentialException,
                    "Expected InvalidCredentialException but got: " + e.getClass().getName());
            assertTrue(e.getMessage().contains("401"),
                    "Error message should contain status code 401");
        }
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 1);
    }

    @Test
    public void testMissingUsername() {
        wireMockServer.stubFor(get(urlEqualTo("/test"))
                .willReturn(aResponse().withStatus(401)));

        var config = new TestConfiguration(wireMockServer.port());
        config.testEndpoint = "/test";
        config.username = "";
        config.password = new GuardedString("password".toCharArray());

        TestRestConnector connector = new TestRestConnector(config);
        connector.init(config);

        try {
            connector.test();
            fail("Expected exception was not thrown");
        } catch (Exception e) {
            assertTrue(e instanceof org.identityconnectors.framework.common.exceptions.ConnectionFailedException ||
                    e instanceof org.identityconnectors.framework.common.exceptions.InvalidCredentialException);
        }
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 1);
    }

    @Test
    public void testMissingAuthorizationHeader() {
        wireMockServer.stubFor(get(urlEqualTo("/test"))
                .willReturn(aResponse().withStatus(401)));

        var config = new TestConfiguration(wireMockServer.port());
        config.testEndpoint = "/test";

        TestRestConnector connector = new TestRestConnector(config);
        connector.init(config);

        try {
            connector.test();
            fail("Expected exception was not thrown");
        } catch (Exception e) {
            assertTrue(e instanceof org.identityconnectors.framework.common.exceptions.ConnectionFailedException ||
                    e instanceof org.identityconnectors.framework.common.exceptions.InvalidCredentialException);
        }
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 1);
    }

    @Test
    public void testErrorDoesNotLeakCredentials() {
        wireMockServer.stubFor(get(urlEqualTo("/secure-test"))
                .willReturn(aResponse().withStatus(401).withBody("Authentication required")));

        var config = new TestConfiguration(wireMockServer.port());
        config.testEndpoint = "/secure-test";
        config.username = "testuser";
        config.password = new GuardedString("secret-password".toCharArray());

        TestRestConnector connector = new TestRestConnector(config);
        connector.init(config);

        try {
            connector.test();
            fail("Expected exception was not thrown");
        } catch (Exception e) {
            assertTrue(e instanceof org.identityconnectors.framework.common.exceptions.InvalidCredentialException);
            assertFalse(e.getMessage().contains("secret-password"),
                    "Error message should not contain password");
            assertFalse(e.getMessage().contains("testuser"),
                    "Error message should not contain username");
        }
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 1);
    }

    /**
     * Built-in basic auth sends Authorization: Basic <value> without any Groovy script.
     */
    @Test
    public void testBuiltinHttpBasicSentToTestEndpoint() {
        String expectedHeader = "Basic " + Base64.getEncoder().encodeToString("admin:s3cr3t".getBytes());
        wireMockServer.stubFor(get(urlEqualTo(API_ENDPOINT))
                .withHeader("Authorization", equalTo(expectedHeader))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"ok\"}")));

        var config = new TestConfiguration(wireMockServer.port());
        config.testEndpoint = API_ENDPOINT;
        config.username = "admin";
        config.password = new GuardedString("s3cr3t".toCharArray());

        var connector = new ScriptConnector(null);
        connector.init(config);
        connector.test();

        assertEquals(wireMockServer.findAll(getRequestedFor(urlEqualTo(API_ENDPOINT))
                .withHeader("Authorization", equalTo(expectedHeader))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 1);
    }

    /**
     * implementation block sets a static Basic auth header.
     */
    @Test
    public void testStaticHeader() {
        wireMockServer.stubFor(get(urlEqualTo(API_ENDPOINT))
                .withHeader("Authorization", equalTo("Basic dXNlcjpwYXNz"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"ok\"}")));

        var script = """
                authentication {
                    rest {
                        basic {
                            implementation {
                                request.header("Authorization", "Basic dXNlcjpwYXNz")
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
                .withHeader("Authorization", equalTo("Basic dXNlcjpwYXNz"))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 1);
    }

    /**
     * decrypt() constructs Basic auth header from configuration credentials.
     */
    @Test
    public void testDecryptCredentials() {
        String expectedHeader = "Basic " + Base64.getEncoder().encodeToString("user:pass".getBytes());
        wireMockServer.stubFor(get(urlEqualTo(API_ENDPOINT))
                .withHeader("Authorization", equalTo(expectedHeader))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"ok\"}")));

        var script = """
                authentication {
                    rest {
                        basic {
                            implementation {
                                def creds = configuration.restUsername + ":" + decrypt(configuration.restPassword)
                                request.header("Authorization", "Basic " + creds.bytes.encodeBase64())
                            }
                        }
                    }
                }
                """;

        var config = new TestConfiguration(wireMockServer.port());
        config.testEndpoint = API_ENDPOINT;
        config.username = "user";
        config.password = new GuardedString("pass".toCharArray());
        var connector = new ScriptConnector(script);
        connector.init(config);
        connector.test();

        assertEquals(wireMockServer.findAll(getRequestedFor(urlEqualTo(API_ENDPOINT))
                .withHeader("Authorization", equalTo(expectedHeader))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 1);
    }

    /**
     * Computed credentials cached in ctx are reused across requests.
     */
    @Test
    public void testCredentialCaching() {
        String expectedHeader = "Basic " + Base64.getEncoder().encodeToString("user:pass".getBytes());
        wireMockServer.stubFor(get(urlEqualTo(API_ENDPOINT))
                .withHeader("Authorization", equalTo(expectedHeader))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"ok\"}")));

        var script = """
                authentication {
                    rest {
                        basic {
                            implementation {
                                if (!ctx["credentials"]) {
                                    def creds = configuration.restUsername + ":" + decrypt(configuration.restPassword)
                                    ctx["credentials"] = creds.bytes.encodeBase64().toString()
                                }
                                request.header("Authorization", "Basic " + ctx["credentials"])
                            }
                        }
                    }
                }
                """;

        var config = new TestConfiguration(wireMockServer.port());
        config.testEndpoint = API_ENDPOINT;
        config.username = "user";
        config.password = new GuardedString("pass".toCharArray());
        var connector = new ScriptConnector(script);
        connector.init(config);
        connector.test();
        connector.test();

        assertEquals(wireMockServer.findAll(getRequestedFor(urlEqualTo(API_ENDPOINT))
                .withHeader("Authorization", equalTo(expectedHeader))).size(), 2);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 2);
    }

    /**
     * Groovy implementation block must override the built-in even when username+password are configured.
     */
    @Test
    public void testGroovyImplementationOverridesBuiltinWhenCredentialsConfigured() {
        wireMockServer.stubFor(get(urlEqualTo("/api"))
                .withHeader("Authorization", equalTo("Bearer groovy-override"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"ok\"}")));

        var script = """
                authentication {
                    rest {
                        basic {
                            implementation {
                                request.header("Authorization", "Bearer groovy-override")
                            }
                        }
                    }
                }
                """;

        var config = new TestConfiguration(wireMockServer.port());
        config.testEndpoint = "/api";
        config.username = "user";
        config.password = new GuardedString("pass".toCharArray());
        var connector = new ScriptConnector(script);
        connector.init(config);
        connector.test();

        assertEquals(wireMockServer.findAll(getRequestedFor(urlEqualTo("/api"))
                .withHeader("Authorization", equalTo("Bearer groovy-override"))).size(), 1,
                "Groovy implementation block should override built-in basic auth");
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 1);
    }

    private static class TestConfiguration extends BaseTestConfiguration
            implements RestClientConfiguration.BasicAuthorization {

        String testEndpoint;
        String username;
        GuardedString password;

        TestConfiguration(int port) {
            super(port);
        }

        @Override
        public String getRestTestEndpoint() {
            return testEndpoint;
        }

        @Override
        public String getRestUsername() {
            return username;
        }

        @Override
        public GuardedString getRestPassword() {
            return password;
        }
    }
}
