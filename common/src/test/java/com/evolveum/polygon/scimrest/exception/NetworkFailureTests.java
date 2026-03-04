/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.exception;

import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.*;
import org.identityconnectors.common.security.GuardedString;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.*;

@SuppressWarnings("unchecked")

/**
 * Tests for network failure scenarios (connection refused, DNS failures, etc.)
 */
public class NetworkFailureTests extends WireMockTestSupport {

    @AfterMethod
    public void tearDown() {
        tearDownWireMock();
    }

    @Test
    public void testConnectionRefusedBecomesConnectionFailed() {
        // When WireMock server is not running or port is wrong
        // The connector should receive ConnectionFailedException
        TestConfiguration config = new TestConfiguration(9999); // Non-open port
        config.setRestTestEndpoint("/test");

        TestRestConnector connector = new TestRestConnector(config);
        connector.init(config);

        try {
            connector.test();
            fail("Expected ConnectionFailedException was not thrown");
        } catch (Exception e) {
            assertTrue(e instanceof org.identityconnectors.framework.common.exceptions.ConnectionFailedException,
                "Expected ConnectionFailedException but got: " + e.getClass().getName());
        }
    }

    @Test
    public void testDnsResolutionFailureBecomesConnectionFailed() {
        // Use invalid hostname that won't resolve
        TestConfiguration config = new TestConfiguration(0);
        config.setBaseAddress("http://invalid-host-that-does-not-exist-12345.com:8080");
        config.setRestTestEndpoint("/test");

        TestRestConnector connector = new TestRestConnector((BaseRestGroovyConnectorConfiguration) config);
        connector.init(config);

        try {
            connector.test();
            fail("Expected ConnectionFailedException was not thrown");
        } catch (Exception e) {
            assertTrue(e instanceof org.identityconnectors.framework.common.exceptions.ConnectionFailedException,
                "Expected ConnectionFailedException but got: " + e.getClass().getName());
        }
    }

    @Test
    public void testNetworkTimeoutFromWireMock() {
        setUpWireMock();

        // Response with 5-second delay with 2-second timeout
        wireMockServer.stubFor(get(urlEqualTo("/timeout"))
            .willReturn(aResponse().withStatus(200).withFixedDelay(5000)));

        TestConfiguration config = new TestConfiguration(wireMockServer.port());
        config.setTimeoutSeconds(2);
        config.setRestTestEndpoint("/timeout");

        TestRestConnector connector = new TestRestConnector(config);
        connector.init(config);

        try {
            connector.test();
            fail("Expected timeout exception was not thrown");
        } catch (Exception e) {
            // Timeout exception (ConnectionFailed or ConnectionBroken)
            assertTrue(e instanceof org.identityconnectors.framework.common.exceptions.ConnectionFailedException ||
                    e instanceof org.identityconnectors.framework.common.exceptions.ConnectionBrokenException,
                "Expected timeout exception but got: " + e.getClass().getName());
        }
    }

    @Test
    public void testMultipleNetworkFailures() {
        // Test that each network failure produces the correct exception type
        int[] ports = {9990, 9991, 9992}; // Non-open ports

        for (int port : ports) {
            TestConfiguration config = new TestConfiguration(port);
            config.setRestTestEndpoint("/test");

            TestRestConnector connector = new TestRestConnector(config);
            connector.init(config);

            try {
                connector.test();
                fail("Expected exception for port " + port);
            } catch (Exception e) {
                assertTrue(e instanceof org.identityconnectors.framework.common.exceptions.ConnectionFailedException,
                    "Port " + port + " should produce ConnectionFailedException but got: " + e.getClass().getName());
            }
        }
    }

    @Test
    public void testConnectionFailureWithAuthenticationConfigured() {
        // Network failure should still occur even with auth configured
        TestConfiguration config = new TestConfiguration(9998);
        config.setRestTestEndpoint("/test");
        config.setRestUsername("testuser");
        config.setRestPassword(new GuardedString("testpass".toCharArray()));

        TestRestConnector connector = new TestRestConnector(config);
        connector.init(config);

        try {
            connector.test();
            fail("Expected ConnectionFailedException was not thrown");
        } catch (Exception e) {
            assertTrue(e instanceof org.identityconnectors.framework.common.exceptions.ConnectionFailedException,
                "Expected ConnectionFailedException but got: " + e.getClass().getName());
        }
    }

    @Test
    public void testTimeoutExceptionIncludesStacktrace() {
        setUpWireMock();

        wireMockServer.stubFor(get(urlEqualTo("/test"))
            .willReturn(aResponse().withStatus(200).withFixedDelay(5000)));

        TestConfiguration config = new TestConfiguration(wireMockServer.port());
        config.setTimeoutSeconds(2);
        config.setRestTestEndpoint("/test");

        TestRestConnector connector = new TestRestConnector(config);
        connector.init(config);

        try {
            connector.test();
            fail("Expected timeout exception was not thrown");
        } catch (Exception e) {
            assertTrue(e instanceof org.identityconnectors.framework.common.exceptions.ConnectionFailedException ||
                    e instanceof org.identityconnectors.framework.common.exceptions.ConnectionBrokenException);
            // Verify exception has stacktrace
            assertNotNull(e.getStackTrace(),
                "Exception should have stacktrace for debugging");
        }
    }

    private static class TestConfiguration extends BaseRestGroovyConnectorConfiguration
            implements RestClientConfiguration.BasicAuthorization {
        private final int port;
        private String baseAddress = "http://localhost";
        private String testEndpoint = "/test";
        private String username;
        private GuardedString password;

        TestConfiguration(int port) {
            this.port = port;
        }

        @Override
        public String getBaseAddress() {
            return baseAddress + ":" + port;
        }

        public void setBaseAddress(String baseAddress) {
            this.baseAddress = baseAddress;
        }

        @Override
        public String getRestTestEndpoint() {
            return testEndpoint;
        }

        public void setRestTestEndpoint(String endpoint) {
            testEndpoint = endpoint;
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
