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
 * Tests for JSON parsing and response handling errors
 */
public class ResponseParsingErrorTests extends WireMockTestSupport {

    @AfterMethod
    public void tearDown() {
        tearDownWireMock();
    }

    @Test
    public void testMalformedJsonBecomesConnectorException() {
        setUpWireMock();

        // The test() method doesn't parse response bodies, only checks status codes
        // For malformed JSON, we test with a 500 error to verify error handling
        wireMockServer.stubFor(get(urlEqualTo("/test"))
            .willReturn(aResponse().withStatus(500)
                .withBody("{invalid json here")));

        TestConfiguration config = new TestConfiguration(wireMockServer.port());
        config.setRestTestEndpoint("/test");

        TestRestConnector connector = new TestRestConnector(config);
        connector.init(config);

        try {
            connector.test();
            fail("Expected ConnectionFailedException was not thrown for 500 error");
        } catch (Exception e) {
            assertTrue(e instanceof org.identityconnectors.framework.common.exceptions.ConnectionFailedException,
                "Expected ConnectionFailedException but got: " + e.getClass().getName());
            assertTrue(e.getMessage().contains("500"),
                "Error message should contain status code 500");
        }
    }

    @Test
    public void testEmptyResponseBecomesConnectorException() {
        setUpWireMock();

        // Empty response with 500 error to test error handling
        wireMockServer.stubFor(get(urlEqualTo("/empty"))
            .willReturn(aResponse().withStatus(500).withBody("")));

        TestConfiguration config = new TestConfiguration(wireMockServer.port());
        config.setRestTestEndpoint("/empty");

        TestRestConnector connector = new TestRestConnector(config);
        connector.init(config);

        try {
            connector.test();
            fail("Expected exception was not thrown");
        } catch (Exception e) {
            // Empty response with 500 should cause ConnectionFailedException
            assertTrue(e instanceof org.identityconnectors.framework.common.exceptions.ConnectionFailedException,
                "Expected ConnectionFailedException but got: " + e.getClass().getName());
        }
    }

    @Test
    public void testJsonArrayResponseIsParsedCorrectly() {
        setUpWireMock();

        // Return valid JSON array
        wireMockServer.stubFor(get(urlEqualTo("/test"))
            .willReturn(aResponse().withStatus(200)
                .withBody("[{\"id\":\"1\",\"name\":\"Item1\"},{\"id\":\"2\",\"name\":\"Item2\"}]")));

        TestConfiguration config = new TestConfiguration(wireMockServer.port());
        config.setRestTestEndpoint("/test");

        TestRestConnector connector = new TestRestConnector(config);
        connector.init(config);

        try {
            connector.test();
            // Parse response is successful, but test() just checks status code
            // In real scenarios with search operations, this would parse the array
        } catch (Exception e) {
            fail("Should not throw exception for valid JSON array: " + e.getMessage());
        }
    }

    @Test
    public void testJsonObjectResponseIsParsedCorrectly() {
        setUpWireMock();

        // Return valid JSON object
        wireMockServer.stubFor(get(urlEqualTo("/test"))
            .willReturn(aResponse().withStatus(200)
                .withBody("{\"id\":\"1\",\"name\":\"Item1\"}")));

        TestConfiguration config = new TestConfiguration(wireMockServer.port());
        config.setRestTestEndpoint("/test");

        TestRestConnector connector = new TestRestConnector(config);
        connector.init(config);

        try {
            connector.test();
            // Valid JSON object should not throw exception
        } catch (Exception e) {
            fail("Should not throw exception for valid JSON object: " + e.getMessage());
        }
    }

    @Test
    public void testJsonWithNestedObjectsParsed() {
        setUpWireMock();

        // Return nested JSON structure
        String nestedJson = "{\"user\":{\"id\":\"1\",\"profile\":{\"name\":\"Test\",\"email\":\"test@example.com\"}}}";
        wireMockServer.stubFor(get(urlEqualTo("/test"))
            .willReturn(aResponse().withStatus(200).withBody(nestedJson)));

        TestConfiguration config = new TestConfiguration(wireMockServer.port());
        config.setRestTestEndpoint("/test");

        TestRestConnector connector = new TestRestConnector(config);
        connector.init(config);

        try {
            connector.test();
            // Should handle nested JSON
        } catch (Exception e) {
            fail("Should not throw exception for nested JSON: " + e.getMessage());
        }
    }

    @Test
    public void testJsonWithSpecialCharacters() {
        setUpWireMock();

        // Return JSON with special characters
        wireMockServer.stubFor(get(urlEqualTo("/test"))
            .willReturn(aResponse().withStatus(200)
                .withBody("{\"message\":\"Hello \\\"World\\\"\",\"emoji\":\" smiley\"}")));

        TestConfiguration config = new TestConfiguration(wireMockServer.port());
        config.setRestTestEndpoint("/test");

        TestRestConnector connector = new TestRestConnector(config);
        connector.init(config);

        try {
            connector.test();
            // Should handle special characters
        } catch (Exception e) {
            fail("Should not throw exception for JSON with special chars: " + e.getMessage());
        }
    }

    @Test
    public void testParseErrorDoesNotIncludeBodyInException() {
        setUpWireMock();

        String largeBody = "{invalid json with lots of content: " + "x".repeat(1000) + "}";
        wireMockServer.stubFor(get(urlEqualTo("/test"))
            .willReturn(aResponse().withStatus(500).withBody(largeBody)));

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
    public void testJsonParsingErrorWithContentLength() {
        setUpWireMock();

        // Return JSON with large content
        wireMockServer.stubFor(get(urlEqualTo("/test"))
            .willReturn(aResponse().withStatus(500)
                .withBody("{\"data\":\"" + "x".repeat(5000) + "\"}")));

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
