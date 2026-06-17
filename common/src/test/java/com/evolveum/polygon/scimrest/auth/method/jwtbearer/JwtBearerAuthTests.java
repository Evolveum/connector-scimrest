/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.auth.method.jwtbearer;

import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.support.WireMockTestSupport;
import org.identityconnectors.common.security.GuardedString;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Base64;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.*;

public class JwtBearerAuthTests extends WireMockTestSupport {

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
     * Built-in JWT bearer sends a signed JWT in the Authorization header.
     */
    @Test
    public void testBuiltinJwtBearerSetsAuthorizationHeader() {
        wireMockServer.stubFor(get(urlEqualTo(API_ENDPOINT))
                .withHeader("Authorization", matching("Bearer .+"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"ok\"}")));

        var config = new TestConfiguration(wireMockServer.port());
        config.testEndpoint = API_ENDPOINT;
        config.jwtTokenName = "Bearer";
        config.jwtAlgorithm = "HS256";
        config.jwtSecret = new GuardedString("my-test-secret".toCharArray());
        config.jwtPayload = "{\"sub\":\"test\"}";

        var connector = new ScriptConnector(null);
        connector.init(config);
        connector.test();

        assertEquals(wireMockServer.findAll(getRequestedFor(urlEqualTo(API_ENDPOINT))
                .withHeader("Authorization", matching("Bearer .+"))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 1);
    }

    /**
     * Built-in JWT bearer puts token into query parameter when localization is "query".
     */
    @Test
    public void testBuiltinJwtBearerSetsQueryParameter() {
        wireMockServer.stubFor(get(urlPathEqualTo(API_ENDPOINT))
                .withQueryParam("token", matching(".+"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"ok\"}")));

        var config = new TestConfiguration(wireMockServer.port());
        config.testEndpoint = API_ENDPOINT;
        config.jwtTokenName = "token";
        config.jwtAlgorithm = "HS256";
        config.jwtSecret = new GuardedString("my-test-secret".toCharArray());
        config.jwtLocalization = "query";
        config.jwtPayload = "{\"sub\":\"test\"}";

        var connector = new ScriptConnector(null);
        connector.init(config);
        connector.test();

        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(API_ENDPOINT))
                .withQueryParam("token", matching(".+"))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 1);
    }

    /**
     * jwtBearer { implementation {} } builds a real JWT via JwtAssertionBuilder;
     * custom claims and a custom header (kid) are verified by decoding the token.
     */
    @Test
    public void testGroovyJwtBearerCustomClaimsAndHeaders() {
        wireMockServer.stubFor(get(urlEqualTo(API_ENDPOINT))
                .withHeader("Authorization", matching("Bearer [A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"ok\"}")));

        var script = """
                authentication {
                    rest {
                        jwtBearer {
                            implementation {
                                def jwt = newJwt()
                                    .header("kid", "key-1")
                                    .claim("sub", "test-user")
                                    .claim("role", "admin")
                                    .sign("HS256", decrypt(configuration.restJwtSecret).bytes)
                                request.header("Authorization", "Bearer " + jwt)
                            }
                        }
                    }
                }
                """;

        var config = new TestConfiguration(wireMockServer.port());
        config.testEndpoint = API_ENDPOINT;
        config.jwtSecret = new GuardedString("test-secret".toCharArray());
        var connector = new ScriptConnector(script);
        connector.init(config);
        connector.test();

        var requests = wireMockServer.findAll(getRequestedFor(urlEqualTo(API_ENDPOINT)));
        assertEquals(requests.size(), 1);
        String[] parts = requests.get(0).getHeader("Authorization")
                .substring("Bearer ".length()).split("\\.");
        assertEquals(parts.length, 3);
        String headerJson  = new String(Base64.getUrlDecoder().decode(parts[0]));
        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
        assertTrue(headerJson.contains("\"kid\":\"key-1\""));
        assertTrue(payloadJson.contains("\"sub\":\"test-user\""));
        assertTrue(payloadJson.contains("\"role\":\"admin\""));
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 1);
    }

    /**
     * JWT built in implementation {} is cached in ctx and reused across requests.
     */
    @Test
    public void testGroovyJwtBearerTokenCaching() {
        wireMockServer.stubFor(get(urlEqualTo(API_ENDPOINT))
                .withHeader("Authorization", matching("Bearer [A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"ok\"}")));

        var script = """
                authentication {
                    rest {
                        jwtBearer {
                            implementation {
                                if (!ctx["token"]) {
                                    ctx["token"] = newJwt()
                                        .claim("sub", "cached-user")
                                        .sign("HS256", decrypt(configuration.restJwtSecret).bytes)
                                }
                                request.header("Authorization", "Bearer " + ctx["token"])
                            }
                        }
                    }
                }
                """;

        var config = new TestConfiguration(wireMockServer.port());
        config.testEndpoint = API_ENDPOINT;
        config.jwtSecret = new GuardedString("test-secret".toCharArray());
        var connector = new ScriptConnector(script);
        connector.init(config);
        connector.test();
        connector.test();

        var requests = wireMockServer.findAll(getRequestedFor(urlEqualTo(API_ENDPOINT)));
        assertEquals(requests.size(), 2);
        assertEquals(requests.get(0).getHeader("Authorization"), requests.get(1).getHeader("Authorization"));
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 2);
    }

    private static class TestConfiguration extends BaseTestConfiguration
            implements RestClientConfiguration.JwtBearerAuthorization {

        String testEndpoint;
        String jwtTokenName;
        String jwtAlgorithm;
        GuardedString jwtSecret;
        Boolean jwtSecretBase64Encoded;
        String jwtLocalization;
        String jwtPayload;

        TestConfiguration(int port) {
            super(port);
        }

        @Override
        public String getRestTestEndpoint() {
            return testEndpoint;
        }

        @Override
        public String getRestJwtTokenName() {
            return jwtTokenName;
        }

        @Override
        public String getRestJwtAlgorithm() {
            return jwtAlgorithm;
        }

        @Override
        public GuardedString getRestJwtSecret() {
            return jwtSecret;
        }

        @Override
        public Boolean getRestJwtSecretBase64Encoded() {
            return jwtSecretBase64Encoded;
        }

        @Override
        public String getRestJwtPayload() {
            return jwtPayload;
        }

        @Override
        public String getRestJwtLocation() {
            return jwtLocalization;
        }
    }
}
