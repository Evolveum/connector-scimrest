/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.exception;

import org.identityconnectors.common.security.GuardedString;
import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.*;

/**
 * Tests for connection timeout scenarios
 */
public class ConnectionTimeoutTests extends WireMockTestSupport {

    @AfterMethod
    public void tearDown() {
        tearDownWireMock();
    }

    @Test
    public void testConnectionTimeoutWithNetworkDelay() {
        setUpWireMock();

        // Use 2-second timeout to test timeout configuration works
        TestConfiguration config = new TestConfiguration(wireMockServer.port());
        config.setTimeoutSeconds(2);
        config.setRestTestEndpoint("/timeout");

        wireMockServer.stubFor(get(urlEqualTo("/timeout"))
            .willReturn(aResponse().withStatus(200).withFixedDelay(5000)));

        TestRestConnector connector = new TestRestConnector(config);
        connector.init(config);

        long start = System.currentTimeMillis();
        try {
            connector.test();
            long elapsed = System.currentTimeMillis() - start;
            fail("Expected timeout exception but request completed in " + elapsed + "ms");
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            assertTrue(e instanceof org.identityconnectors.framework.common.exceptions.ConnectionFailedException ||
                    e instanceof org.identityconnectors.framework.common.exceptions.ConnectionBrokenException,
                "Expected ConnectionFailedException or ConnectionBrokenException but got: " + e.getClass().getName() + " msg: " + msg);
        }
    }

    private static class TestConfiguration extends BaseRestGroovyConnectorConfiguration
            implements RestClientConfiguration.BasicAuthorization {
        private final int port;
        private String username;
        private GuardedString password;

        TestConfiguration(int port) {
            this.port = port;
        }

        @Override
        public String getBaseAddress() {
            return "http://localhost:" + port;
        }

        @Override
        public String getRestUsername() {
            return username;
        }

        public void setRestUsername(String username) {
            this.username = username;
        }

        @Override
        public GuardedString getRestPassword() {
            return password;
        }

        public void setRestPassword(GuardedString password) {
            this.password = password;
        }
    }
}
