/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.auth.preference;

import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.config.ScimClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.connector.BaseRestGroovyConnectorConfiguration;
import com.evolveum.polygon.scimrest.support.WireMockTestSupport;
import org.identityconnectors.common.security.GuardedString;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.testng.Assert.assertEquals;

/**
 * {@code test()} on a SCIM connector with a REST auth {@code preference} declared must still
 * probe the SCIM test endpoint ({@code scimBaseUrl + restTestEndpoint}), so a wrong or
 * unreachable endpoint is actually caught. Currently it is not: {@code test()} checks
 * {@code context.rest().isPreferenceActive()} first, and
 * {@link com.evolveum.polygon.scimrest.groovy.auth.AuthPreferenceManager#doProbe} silently
 * defaults to the first preferred method (no request sent) whenever the REST {@code baseAddress}
 * is unset - which it always is for a SCIM-only connector, since SCIM uses {@code scimBaseUrl}
 * instead. So the SCIM test endpoint is never reached, and {@code test()} reports success
 * regardless of whether the configured endpoint would actually succeed or fail.
 */
public class ScimAuthPreferenceSkipsScimProbeTest extends WireMockTestSupport {

    private static final String TEST_ENDPOINT = "/health";
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

    @Test
    public void testScimTestEndpointIsProbedEvenWithRestPreferenceConfigured() {
        wireMockServer.stubFor(get(urlEqualTo(TEST_ENDPOINT))
                .willReturn(aResponse().withStatus(200)));
        // Schema discovery during init() fetches these, independently of the test endpoint.
        wireMockServer.stubFor(get(urlEqualTo("/Schemas"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/scim+json")
                        .withBody(EMPTY_LIST_RESPONSE)));
        wireMockServer.stubFor(get(urlEqualTo("/ResourceTypes"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/scim+json")
                        .withBody(EMPTY_LIST_RESPONSE)));

        var script = """
                authentication {
                    rest {
                        apiKey {
                            implementation {
                                request.header("X-Api-Key", "k1")
                            }
                        }
                        bearer {
                            implementation {
                                request.header("Authorization", "Bearer t1")
                            }
                        }
                        preference apiKey, bearer
                    }
                }
                """;

        var config = new TestConfiguration(wireMockServer.port());
        config.setRestTestEndpoint(TEST_ENDPOINT);
        var connector = new ScriptConnector(script);
        connector.init(config);
        connector.test();

        // The SCIM test endpoint - reachable and configured via scimBaseUrl + restTestEndpoint -
        // must actually be requested once, so a wrong endpoint would be caught as a failure.
        assertEquals(wireMockServer.findAll(getRequestedFor(urlEqualTo(TEST_ENDPOINT))).size(), 1);
    }

    private static class TestConfiguration extends BaseRestGroovyConnectorConfiguration
            implements RestClientConfiguration.ApiKeyAuthorization,
            RestClientConfiguration.BearerTokenAuthorization,
            ScimClientConfiguration {

        private final int port;

        TestConfiguration(int port) {
            this.port = port;
            // Deliberately not calling setBaseAddress() - this is a SCIM-only connector,
            // which uses scimBaseUrl (below) instead of the generic REST baseAddress.
        }

        @Override
        public String getScimBaseUrl() {
            return "http://localhost:" + port;
        }

        @Override
        public GuardedString getRestApiKey() {
            return new GuardedString("k1".toCharArray());
        }

        @Override
        public String getRestApiKeyName() {
            return "X-Api-Key";
        }

        @Override
        public String getRestApiKeyLocation() {
            return "header";
        }

        @Override
        public GuardedString getRestTokenValue() {
            return new GuardedString("t1".toCharArray());
        }
    }
}
