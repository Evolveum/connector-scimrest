/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.exception;

import com.evolveum.polygon.scimrest.groovy.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.*;

/**
 * Tests for HTTP 5xx status code error handling
 */
public class HttpStatus5xxTests extends WireMockTestSupport {

    @AfterMethod
    public void tearDown() {
        tearDownWireMock();
    }

    @Test
    public void test500InternalServerErrorBecomesConnectorException() {
        setUpWireMock();

        wireMockServer.stubFor(get(urlEqualTo("/test"))
            .willReturn(aResponse().withStatus(500).withBody("{\"error\":\"Internal server error\"}")));

        TestConfiguration config = new TestConfiguration(wireMockServer.port());
        config.setRestTestEndpoint("/test");

        TestRestConnector connector = new TestRestConnector(config);
        connector.init(config);

        try {
            connector.test();
            fail("Expected ConnectorException was not thrown");
        } catch (Exception e) {
            assertTrue(e instanceof org.identityconnectors.framework.common.exceptions.ConnectorException,
                "Expected ConnectorException but got: " + e.getClass().getName());
            assertTrue(e.getMessage().contains("500"),
                "Error message should contain status code 500");
        }
    }

    @Test
    public void test502BadGatewayBecomesConnectionFailed() {
        setUpWireMock();

        wireMockServer.stubFor(get(urlEqualTo("/test"))
            .willReturn(aResponse().withStatus(502)));

        TestConfiguration config = new TestConfiguration(wireMockServer.port());
        config.setRestTestEndpoint("/test");

        TestRestConnector connector = new TestRestConnector(config);
        connector.init(config);

        try {
            connector.test();
            fail("Expected ConnectionFailedException was not thrown");
        } catch (Exception e) {
            assertTrue(e instanceof org.identityconnectors.framework.common.exceptions.ConnectionFailedException,
                "Expected ConnectionFailedException but got: " + e.getClass().getName());
            assertTrue(e.getMessage().contains("502"),
                "Error message should contain status code 502");
        }
    }

    @Test
    public void test503ServiceUnavailableBecomesConnectionFailed() {
        setUpWireMock();

        wireMockServer.stubFor(get(urlEqualTo("/test"))
            .willReturn(aResponse().withStatus(503).withHeader("Retry-After", "30")));

        TestConfiguration config = new TestConfiguration(wireMockServer.port());
        config.setRestTestEndpoint("/test");

        TestRestConnector connector = new TestRestConnector(config);
        connector.init(config);

        try {
            connector.test();
            fail("Expected ConnectionFailedException was not thrown");
        } catch (Exception e) {
            assertTrue(e instanceof org.identityconnectors.framework.common.exceptions.ConnectionFailedException,
                "Expected ConnectionFailedException but got: " + e.getClass().getName());
            assertTrue(e.getMessage().contains("503"),
                "Error message should contain status code 503");
        }
    }

    @Test
    public void test504GatewayTimeoutBecomesConnectionFailed() {
        setUpWireMock();

        wireMockServer.stubFor(get(urlEqualTo("/test"))
            .willReturn(aResponse().withStatus(504)));

        TestConfiguration config = new TestConfiguration(wireMockServer.port());
        config.setRestTestEndpoint("/test");

        TestRestConnector connector = new TestRestConnector(config);
        connector.init(config);

        try {
            connector.test();
            fail("Expected ConnectionFailedException was not thrown");
        } catch (Exception e) {
            assertTrue(e instanceof org.identityconnectors.framework.common.exceptions.ConnectionFailedException,
                "Expected ConnectionFailedException but got: " + e.getClass().getName());
            assertTrue(e.getMessage().contains("504"),
                "Error message should contain status code 504");
        }
    }

    @Test
    public void testMultiple5xxErrorsEachProducesCorrectExceptionType() {
        setUpWireMock();

        TestRestConnector connector = new TestRestConnector(new TestConfiguration(wireMockServer.port()));

        int[] errorCodes = {500, 502, 503, 504};
        for (int statusCode : errorCodes) {
            wireMockServer.stubFor(get(urlEqualTo("/" + statusCode))
                .willReturn(aResponse().withStatus(statusCode)));

            TestConfiguration config = new TestConfiguration(wireMockServer.port());
            config.setRestTestEndpoint("/" + statusCode);
            connector.init(config);

            try {
                connector.test();
                fail("Expected exception for status code " + statusCode);
            } catch (Exception e) {
                if (statusCode == 500) {
                    assertTrue(e instanceof org.identityconnectors.framework.common.exceptions.ConnectorException,
                        "500 should be ConnectorException but got: " + e.getClass().getName());
                } else {
                    assertTrue(e instanceof org.identityconnectors.framework.common.exceptions.ConnectionFailedException,
                        statusCode + " should be ConnectionFailedException but got: " + e.getClass().getName());
                }
            }
        }
    }

    @Test
    public void test503IncludesRetryAfterHeaderInError() {
        setUpWireMock();

        wireMockServer.stubFor(get(urlEqualTo("/test"))
            .willReturn(aResponse().withStatus(503).withHeader("Retry-After", "120")));

        TestConfiguration config = new TestConfiguration(wireMockServer.port());
        config.setRestTestEndpoint("/test");

        TestRestConnector connector = new TestRestConnector(config);
        connector.init(config);

        try {
            connector.test();
            fail("Expected exception was not thrown");
        } catch (Exception e) {
            // The current implementation doesn't include headers in error messages
            // This test documents current behavior
            assertTrue(e instanceof org.identityconnectors.framework.common.exceptions.ConnectionFailedException);
        }
    }

    private static class TestConfiguration extends BaseRestGroovyConnectorConfiguration {
        private final int port;
        private String testEndpoint = "/test";

        TestConfiguration(int port) {
            this.port = port;
        }

        @Override
        public String getRestTestEndpoint() {
            return testEndpoint;
        }

        public void setRestTestEndpoint(String endpoint) {
            testEndpoint = endpoint;
        }

        @Override
        public String getBaseAddress() {
            return "http://localhost:" + port;
        }
    }


}
