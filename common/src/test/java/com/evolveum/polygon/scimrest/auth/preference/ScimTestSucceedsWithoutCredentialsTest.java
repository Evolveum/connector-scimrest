/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.auth.preference;

import com.evolveum.polygon.scimrest.config.ScimClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.connector.BaseRestGroovyConnectorConfiguration;
import com.evolveum.polygon.scimrest.support.WireMockTestSupport;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.testng.Assert.assertEquals;

/**
 * A SCIM API can be anonymously accessible - {@code test()} must still actually probe it (and
 * succeed) when no authentication method is configured at all, instead of either refusing to
 * initialize the SCIM context ({@code RestConnectorContext.initializeScim} used to require
 * {@code ScimClientConfiguration.hasCredentials}) or silently reporting success without
 * requesting anything.
 */
public class ScimTestSucceedsWithoutCredentialsTest extends WireMockTestSupport {

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
    public void testAnonymousScimTestEndpointSucceeds() {
        wireMockServer.stubFor(get(urlEqualTo(TEST_ENDPOINT))
                .withHeader("Authorization", absent())
                .willReturn(aResponse().withStatus(200)));
        // Schema discovery during init() fetches these, independently of the test endpoint.
        wireMockServer.stubFor(get(urlEqualTo("/Schemas"))
                .withHeader("Authorization", absent())
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/scim+json")
                        .withBody(EMPTY_LIST_RESPONSE)));
        wireMockServer.stubFor(get(urlEqualTo("/ResourceTypes"))
                .withHeader("Authorization", absent())
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/scim+json")
                        .withBody(EMPTY_LIST_RESPONSE)));

        var config = new TestConfiguration(wireMockServer.port());
        config.setRestTestEndpoint(TEST_ENDPOINT);
        var connector = new ScriptConnector(null);
        connector.init(config);

        connector.test();

        assertEquals(wireMockServer.findAll(getRequestedFor(urlEqualTo(TEST_ENDPOINT))).size(), 1);
    }

    /**
     * When there is no SCIM base URL and no REST base address either, nothing can be probed at
     * all - test() must fail loudly instead of silently reporting success.
     */
    @Test(expectedExceptions = ConfigurationException.class)
    public void testThrowsWhenNothingConfiguredAtAll() {
        var config = new NothingConfiguredConfiguration();
        config.setRestTestEndpoint(TEST_ENDPOINT);

        var connector = new ScriptConnector(null);
        connector.init(config);
        connector.test();
    }

    // No Authorization sub-interface implemented at all - this is the "no auth method
    // configured" case, not merely "configured but empty".
    private static class TestConfiguration extends BaseRestGroovyConnectorConfiguration
            implements ScimClientConfiguration {

        private final int port;

        TestConfiguration(int port) {
            this.port = port;
            // Deliberately not calling setBaseAddress() - SCIM-only connector.
        }

        @Override
        public String getScimBaseUrl() {
            return "http://localhost:" + port;
        }
    }

    // No scimBaseUrl, no baseAddress - genuinely nothing to test against.
    private static class NothingConfiguredConfiguration extends BaseRestGroovyConnectorConfiguration
            implements ScimClientConfiguration {

        @Override
        public String getScimBaseUrl() {
            return null;
        }
    }
}
