/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.exception.auth.basic;

import com.evolveum.polygon.scimrest.config.ScimClientConfiguration;
import com.evolveum.polygon.scimrest.exception.WireMockTestSupport;
import com.evolveum.polygon.scimrest.groovy.AbstractGroovyRestConnector;
import com.evolveum.polygon.scimrest.groovy.BaseGroovyConnectorConfiguration;
import org.identityconnectors.common.security.GuardedString;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Base64;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.*;

public class ScimHttpBasicTests extends WireMockTestSupport {

    private static final String SCIM_BASE_PATH = "/scim";
    private static final String SCHEMAS_ENDPOINT = SCIM_BASE_PATH + "/Schemas";
    private static final String RESOURCES_ENDPOINT = SCIM_BASE_PATH + "/ResourceTypes";

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
     * implementation block sets a static Basic auth header on SCIM requests.
     */
    @Test
    public void testStaticHeader() {
        stubScimEndpoints("Authorization", "Basic dXNlcjpwYXNz");

        var script = """
                authentication {
                    scim {
                        httpBasic {
                            implementation {
                                request.header("Authorization", "Basic dXNlcjpwYXNz")
                            }
                        }
                    }
                }
                """;

        createConnector(script, null, null).schema();

        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(SCHEMAS_ENDPOINT))
                .withHeader("Authorization", equalTo("Basic dXNlcjpwYXNz"))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 2);
    }

    /**
     * decrypt() constructs Basic auth header from SCIM configuration credentials.
     */
    @Test
    public void testDecryptCredentials() {
        String expectedHeader = "Basic " + Base64.getEncoder().encodeToString("user:pass".getBytes());
        stubScimEndpoints("Authorization", expectedHeader);

        var script = """
                authentication {
                    scim {
                        httpBasic {
                            implementation {
                                def creds = configuration.scimUsername + ":" + decrypt(configuration.scimPassword)
                                request.header("Authorization", "Basic " + creds.bytes.encodeBase64())
                            }
                        }
                    }
                }
                """;

        createConnector(script, "user", new GuardedString("pass".toCharArray())).schema();

        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(SCHEMAS_ENDPOINT))
                .withHeader("Authorization", equalTo(expectedHeader))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 2);
    }

    /**
     * Computed credentials cached in ctx are reused across SCIM requests.
     */
    @Test
    public void testCredentialCaching() {
        String expectedHeader = "Basic " + Base64.getEncoder().encodeToString("user:pass".getBytes());
        stubScimEndpoints("Authorization", expectedHeader);

        var script = """
                authentication {
                    scim {
                        httpBasic {
                            implementation {
                                if (!ctx["credentials"]) {
                                    def creds = configuration.scimUsername + ":" + decrypt(configuration.scimPassword)
                                    ctx["credentials"] = creds.bytes.encodeBase64().toString()
                                }
                                request.header("Authorization", "Basic " + ctx["credentials"])
                            }
                        }
                    }
                }
                """;

        var connector = createConnector(script, "user", new GuardedString("pass".toCharArray()));
        connector.schema();

        // ctx caching verified across both SCIM discovery requests (Schemas + ResourceTypes)
        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(SCHEMAS_ENDPOINT))
                .withHeader("Authorization", equalTo(expectedHeader))).size(), 1);
        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(RESOURCES_ENDPOINT))
                .withHeader("Authorization", equalTo(expectedHeader))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 2);
    }

    // --- helpers ---

    private void stubScimEndpoints(String headerName, String headerValue) {
        wireMockServer.stubFor(get(urlPathEqualTo(SCHEMAS_ENDPOINT))
                .withHeader(headerName, equalTo(headerValue))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/scim+json")
                        .withBody(EMPTY_LIST_RESPONSE)));
        wireMockServer.stubFor(get(urlPathEqualTo(RESOURCES_ENDPOINT))
                .withHeader(headerName, equalTo(headerValue))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/scim+json")
                        .withBody(EMPTY_LIST_RESPONSE)));
    }

    private AbstractGroovyRestConnector<?> createConnector(String script,
                                                           String username, GuardedString password) {
        var config = new ScimHttpBasicConfig(wireMockServer.port(), username, password);
        var connector = new ScriptConnector(script);
        connector.init(config);
        return connector;
    }

    private class ScimHttpBasicConfig extends BaseGroovyConnectorConfiguration
            implements ScimClientConfiguration.HttpBasic {

        private final int port;
        private final String username;
        private final GuardedString password;

        ScimHttpBasicConfig(int port, String username, GuardedString password) {
            this.port = port;
            this.username = username;
            this.password = password;
        }

        @Override
        public String getScimBaseUrl() {
            return "http://localhost:" + port + SCIM_BASE_PATH;
        }

        @Override
        public String getScimUsername() {
            return username;
        }

        @Override
        public GuardedString getScimPassword() {
            return password;
        }
    }
}
