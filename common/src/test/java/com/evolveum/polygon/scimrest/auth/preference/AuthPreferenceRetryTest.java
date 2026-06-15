/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.auth.preference;

import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.support.WireMockTestSupport;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.*;

/**
 * Tests 401 retry logic — when an API request returns 401 mid-session,
 * the preference manager reprobes and the request is retried once with the new active method.
 */
public class AuthPreferenceRetryTest extends WireMockTestSupport {

    private static final String TEST_ENDPOINT = "/test";
    private static final String API_PATH = "/accounts";

    private static final String AUTH_SCRIPT = """
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

    private static final String SCHEMA_SCRIPT = """
            objectClass("Account") { }
            """;

    private static final String OPERATION_SCRIPT = """
            objectClass("Account") {
                search {
                    endpoint("accounts") {
                        emptyFilterSupported true
                    }
                }
            }
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
     * Primary is selected during probe, then expires mid-session (returns 401).
     * Reprobe selects fallback and the search request is retried successfully.
     */
    @Test
    public void testRetryOn401SelectsFallback() {
        // probe: primary succeeds first time, then fails on reprobe
        wireMockServer.stubFor(get(urlEqualTo(TEST_ENDPOINT))
                .inScenario("auth-switch")
                .whenScenarioStateIs(Scenario.STARTED)
                .withHeader("X-Api-Key", equalTo("primary-key"))
                .willReturn(aResponse().withStatus(200))
                .willSetStateTo("primary-expired"));

        wireMockServer.stubFor(get(urlEqualTo(TEST_ENDPOINT))
                .inScenario("auth-switch")
                .whenScenarioStateIs("primary-expired")
                .withHeader("X-Api-Key", equalTo("primary-key"))
                .willReturn(aResponse().withStatus(401)));

        wireMockServer.stubFor(get(urlEqualTo(TEST_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer fallback-token"))
                .willReturn(aResponse().withStatus(200)));

        // search: primary → 401, fallback → 200
        wireMockServer.stubFor(get(urlEqualTo(API_PATH))
                .withHeader("X-Api-Key", equalTo("primary-key"))
                .willReturn(aResponse().withStatus(401)));

        wireMockServer.stubFor(get(urlEqualTo(API_PATH))
                .withHeader("Authorization", equalTo("Bearer fallback-token"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        var config = new TestConfiguration(wireMockServer.port());
        config.testEndpoint = TEST_ENDPOINT;
        var connector = new ScriptConnector(AUTH_SCRIPT, SCHEMA_SCRIPT, OPERATION_SCRIPT);
        connector.init(config);
        connector.test();  // probe: primary selected, scenario → "primary-expired"

        connector.executeQuery(new ObjectClass("Account"), null, r -> true, new OperationOptionsBuilder().build());

        // initial probe + reprobe both tried primary on TEST_ENDPOINT
        assertEquals(wireMockServer.findAll(
                getRequestedFor(urlEqualTo(TEST_ENDPOINT))
                        .withHeader("X-Api-Key", equalTo("primary-key"))).size(), 2);
        // reprobe selected fallback on TEST_ENDPOINT
        assertEquals(wireMockServer.findAll(
                getRequestedFor(urlEqualTo(TEST_ENDPOINT))
                        .withHeader("Authorization", equalTo("Bearer fallback-token"))).size(), 1);
        // primary tried once on API_PATH (got 401)
        assertEquals(wireMockServer.findAll(
                getRequestedFor(urlEqualTo(API_PATH))
                        .withHeader("X-Api-Key", equalTo("primary-key"))).size(), 1);
        // fallback used for the retry on API_PATH
        assertEquals(wireMockServer.findAll(
                getRequestedFor(urlEqualTo(API_PATH))
                        .withHeader("Authorization", equalTo("Bearer fallback-token"))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 5);
    }

    /**
     * Even when the retry also returns 401, the request is not retried again — no infinite loop.
     * Reprobe re-selects primary (probe endpoint always returns 200), but the API keeps returning 401.
     */
    @Test
    public void testRetryHappensOnlyOnce() {
        // probe: primary always succeeds (initial probe and reprobe both re-select primary)
        wireMockServer.stubFor(get(urlEqualTo(TEST_ENDPOINT))
                .withHeader("X-Api-Key", equalTo("primary-key"))
                .willReturn(aResponse().withStatus(200)));

        // API always returns 401
        wireMockServer.stubFor(get(urlEqualTo(API_PATH))
                .withHeader("X-Api-Key", equalTo("primary-key"))
                .willReturn(aResponse().withStatus(401)));

        var config = new TestConfiguration(wireMockServer.port());
        config.testEndpoint = TEST_ENDPOINT;
        var connector = new ScriptConnector(AUTH_SCRIPT, SCHEMA_SCRIPT, OPERATION_SCRIPT);
        connector.init(config);
        connector.test();

        connector.executeQuery(new ObjectClass("Account"), null, r -> true, new OperationOptionsBuilder().build());

        // TEST_ENDPOINT probed twice: initial probe + reprobe after 401
        assertEquals(wireMockServer.findAll(
                getRequestedFor(urlEqualTo(TEST_ENDPOINT))
                        .withHeader("X-Api-Key", equalTo("primary-key"))).size(), 2);
        // API called exactly twice: original attempt + one retry, both with primary
        assertEquals(wireMockServer.findAll(
                getRequestedFor(urlEqualTo(API_PATH))
                        .withHeader("X-Api-Key", equalTo("primary-key"))).size(), 2);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 4);
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
