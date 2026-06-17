/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.auth.method.jwtbearer;

import com.evolveum.polygon.scimrest.config.ScimClientConfiguration;
import com.evolveum.polygon.scimrest.support.WireMockTestSupport;
import com.evolveum.polygon.scimrest.groovy.AbstractGroovyRestConnector;
import com.evolveum.polygon.scimrest.groovy.BaseGroovyConnectorConfiguration;
import org.identityconnectors.common.security.GuardedString;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Base64;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.*;

public class ScimJwtBearerAuthTests extends WireMockTestSupport {

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
     * Built-in SCIM JWT bearer sends a signed JWT in the Authorization header.
     */
    @Test
    public void testBuiltinScimJwtBearerSetsAuthorizationHeader() {
        stubScimEndpoints("Authorization", matching("Bearer .+"));

        var config = new ScimTestConfiguration(wireMockServer.port());
        config.jwtTokenName = "Bearer";
        config.jwtAlgorithm = "HS256";
        config.jwtSecret = new GuardedString("my-scim-secret".toCharArray());
        config.jwtPayload = "{\"sub\":\"scim-test\"}";

        var connector = new ScriptConnector(null);
        connector.init(config);
        connector.schema();

        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(SCHEMAS_ENDPOINT))
                .withHeader("Authorization", matching("Bearer .+"))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 2);
    }

    /**
     * jwtBearer { implementation {} } builds a real JWT; custom claims and kid header
     * are verified by decoding the token sent in the SCIM request.
     */
    @Test
    public void testScimGroovyJwtBearerCustomClaimsAndHeaders() {
        stubScimEndpoints("Authorization", matching("Bearer [A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"));

        var script = """
                authentication {
                    scim {
                        jwtBearer {
                            implementation {
                                def jwt = newJwt()
                                    .header("kid", "scim-key-1")
                                    .claim("sub", "scim-user")
                                    .claim("scope", "scim:read")
                                    .sign("HS256", decrypt(configuration.scimJwtSecret).bytes)
                                request.header("Authorization", "Bearer " + jwt)
                            }
                        }
                    }
                }
                """;

        var config = new ScimTestConfiguration(wireMockServer.port());
        config.jwtSecret = new GuardedString("test-secret".toCharArray());
        var connector = new ScriptConnector(script);
        connector.init(config);
        connector.schema();

        var requests = wireMockServer.findAll(getRequestedFor(urlPathEqualTo(SCHEMAS_ENDPOINT)));
        assertEquals(requests.size(), 1);
        String[] parts = requests.get(0).getHeader("Authorization")
                .substring("Bearer ".length()).split("\\.");
        assertEquals(parts.length, 3);
        String headerJson  = new String(Base64.getUrlDecoder().decode(parts[0]));
        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
        assertTrue(headerJson.contains("\"kid\":\"scim-key-1\""));
        assertTrue(payloadJson.contains("\"sub\":\"scim-user\""));
        assertTrue(payloadJson.contains("\"scope\":\"scim:read\""));
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 2);
    }

    /**
     * JWT built in implementation {} is cached in ctx; both SCIM discovery requests
     * within a single schema() call reuse the same token.
     */
    @Test
    public void testScimGroovyJwtBearerTokenCaching() {
        stubScimEndpoints("Authorization", matching("Bearer [A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"));

        var script = """
                authentication {
                    scim {
                        jwtBearer {
                            implementation {
                                if (!ctx["token"]) {
                                    ctx["token"] = newJwt()
                                        .claim("sub", "cached-scim-user")
                                        .sign("HS256", decrypt(configuration.scimJwtSecret).bytes)
                                }
                                request.header("Authorization", "Bearer " + ctx["token"])
                            }
                        }
                    }
                }
                """;

        var config = new ScimTestConfiguration(wireMockServer.port());
        config.jwtSecret = new GuardedString("test-secret".toCharArray());
        var connector = new ScriptConnector(script);
        connector.init(config);
        connector.schema();

        var schemasRequests   = wireMockServer.findAll(getRequestedFor(urlPathEqualTo(SCHEMAS_ENDPOINT)));
        var resourcesRequests = wireMockServer.findAll(getRequestedFor(urlPathEqualTo(RESOURCES_ENDPOINT)));
        assertEquals(schemasRequests.size(), 1);
        assertEquals(resourcesRequests.size(), 1);
        assertEquals(schemasRequests.get(0).getHeader("Authorization"),
                resourcesRequests.get(0).getHeader("Authorization"));
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 2);
    }

    // --- helpers ---

    private void stubScimEndpoints(String headerName,
                                   com.github.tomakehurst.wiremock.matching.StringValuePattern headerPattern) {
        wireMockServer.stubFor(get(urlPathEqualTo(SCHEMAS_ENDPOINT))
                .withHeader(headerName, headerPattern)
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/scim+json")
                        .withBody(EMPTY_LIST_RESPONSE)));
        wireMockServer.stubFor(get(urlPathEqualTo(RESOURCES_ENDPOINT))
                .withHeader(headerName, headerPattern)
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

    private class ScimTestConfiguration extends BaseGroovyConnectorConfiguration
            implements ScimClientConfiguration.JwtBearerAuthorization {

        private final int port;
        String jwtTokenName;
        String jwtAlgorithm;
        GuardedString jwtSecret;
        Boolean jwtSecretBase64Encoded;
        String jwtLocalization;
        String jwtPayload;

        ScimTestConfiguration(int port) {
            this.port = port;
        }

        @Override
        public String getScimBaseUrl() {
            return "http://localhost:" + port + SCIM_BASE_PATH;
        }

        @Override
        public String getScimJwtTokenName() {
            return jwtTokenName;
        }

        @Override
        public String getScimJwtAlgorithm() {
            return jwtAlgorithm;
        }

        @Override
        public GuardedString getScimJwtSecret() {
            return jwtSecret;
        }

        @Override
        public Boolean getScimJwtSecretBase64Encoded() {
            return jwtSecretBase64Encoded;
        }

        @Override
        public String getScimJwtPayload() {
            return jwtPayload;
        }

        @Override
        public String getScimJwtLocation() {
            return jwtLocalization;
        }
    }
}
