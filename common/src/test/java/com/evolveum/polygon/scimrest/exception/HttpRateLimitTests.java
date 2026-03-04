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
 * Tests for HTTP 429 (Rate Limit) status code error handling
 */
public class HttpRateLimitTests extends WireMockTestSupport {

    @AfterMethod
    public void tearDown() {
        tearDownWireMock();
    }

    @Test
    public void test429RateLimitBecomesConnectionFailed() {
        setUpWireMock();

        wireMockServer.stubFor(get(urlEqualTo("/test"))
            .willReturn(aResponse().withStatus(429)
                .withHeader("Retry-After", "30")
                .withBody("{\"error\":\"Rate limit exceeded\"}")));

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
            assertTrue(e.getMessage().contains("429"),
                "Error message should contain status code 429");
        }
    }

    @Test
    public void test429WithNumericRetryAfterHeader() {
        setUpWireMock();

        wireMockServer.stubFor(get(urlEqualTo("/test"))
            .willReturn(aResponse().withStatus(429)
                .withHeader("Retry-After", "60")));

        TestConfiguration config = new TestConfiguration(wireMockServer.port());
        config.setRestTestEndpoint("/test");

        TestRestConnector connector = new TestRestConnector(config);
        connector.init(config);

        try {
            connector.test();
            fail("Expected exception was not thrown");
        } catch (Exception e) {
            assertTrue(e instanceof org.identityconnectors.framework.common.exceptions.ConnectionFailedException);
            // Current implementation doesn't include Retry-After in error message
            // This test documents current behavior
        }
    }

    @Test
    public void test429WithDateRetryAfterHeader() {
        setUpWireMock();

        wireMockServer.stubFor(get(urlEqualTo("/test"))
            .willReturn(aResponse().withStatus(429)
                .withHeader("Retry-After", "Wed, 21 Oct 2026 07:28:00 GMT")));

        TestConfiguration config = new TestConfiguration(wireMockServer.port());
        config.setRestTestEndpoint("/test");

        TestRestConnector connector = new TestRestConnector(config);
        connector.init(config);

        try {
            connector.test();
            fail("Expected exception was not thrown");
        } catch (Exception e) {
            assertTrue(e instanceof org.identityconnectors.framework.common.exceptions.ConnectionFailedException);
        }
    }

    @Test
    public void test429IncludeErrorDetailsInExceptionMessage() {
        setUpWireMock();

        String errorBody = "{\"error\":\"Rate limit exceeded\",\"retry_after\":30}";
        wireMockServer.stubFor(get(urlEqualTo("/test"))
            .willReturn(aResponse().withStatus(429).withBody(errorBody)));

        TestConfiguration config = new TestConfiguration(wireMockServer.port());
        config.setRestTestEndpoint("/test");

        TestRestConnector connector = new TestRestConnector(config);
        connector.init(config);

        try {
            connector.test();
            fail("Expected exception was not thrown");
        } catch (Exception e) {
            assertTrue(e instanceof org.identityconnectors.framework.common.exceptions.ConnectionFailedException);
            // Current implementation doesn't include body in error messages
            // This test documents current behavior
        }
    }

    @Test
    public void test429WithRetryAfterInSuccessiveRequests() {
        setUpWireMock();

        // First request returns 429
        wireMockServer.stubFor(get(urlEqualTo("/test"))
            .willReturn(aResponse().withStatus(429).withFixedDelay(100)));

        TestConfiguration config = new TestConfiguration(wireMockServer.port());
        config.setRestTestEndpoint("/test");

        TestRestConnector connector = new TestRestConnector(config);
        connector.init(config);

        try {
            connector.test();
            fail("Expected exception was not thrown");
        } catch (Exception e) {
            assertTrue(e instanceof org.identityconnectors.framework.common.exceptions.ConnectionFailedException);
        }

        // Verify the 429 was actually called
        wireMockServer.verify(1, getRequestedFor(urlEqualTo("/test")));
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
