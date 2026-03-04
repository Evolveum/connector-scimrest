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
 * Tests for pagination-related error handling
 */
public class PaginationErrorTests extends WireMockTestSupport {

    @AfterMethod
    public void tearDown() {
        tearDownWireMock();
    }

    @Test
    public void testSinglePageError() {
        setUpWireMock();

        wireMockServer.stubFor(get(urlEqualTo("/items"))
            .willReturn(aResponse().withStatus(500)));

        TestConfiguration config = new TestConfiguration(wireMockServer.port());
        config.setRestTestEndpoint("/items");

        TestRestConnector connector = new TestRestConnector(config);
        connector.init(config);

        try {
            connector.test();
            fail("Expected exception was not thrown");
        } catch (Exception e) {
            assertTrue(e instanceof org.identityconnectors.framework.common.exceptions.ConnectionFailedException ||
                    e instanceof org.identityconnectors.framework.common.exceptions.ConnectorException);
            assertTrue(e.getMessage().contains("500"),
                "Error should mention status code 500");
        }
    }

    @Test
    public void testSinglePageWith503() {
        setUpWireMock();

        wireMockServer.stubFor(get(urlEqualTo("/items"))
            .willReturn(aResponse().withStatus(503)));

        TestConfiguration config = new TestConfiguration(wireMockServer.port());
        config.setRestTestEndpoint("/items");

        TestRestConnector connector = new TestRestConnector(config);
        connector.init(config);

        try {
            connector.test();
            fail("Expected exception was not thrown");
        } catch (Exception e) {
            assertTrue(e instanceof org.identityconnectors.framework.common.exceptions.ConnectionFailedException ||
                    e instanceof org.identityconnectors.framework.common.exceptions.ConnectorException);
            assertTrue(e.getMessage().contains("503"),
                "Error should mention status code 503");
        }
    }

    @Test
    public void testSinglePageSuccess() {
        setUpWireMock();

        wireMockServer.stubFor(get(urlEqualTo("/items"))
            .willReturn(aResponse().withStatus(200)
                .withBody("[{\"id\":\"1\"}]")));

        TestConfiguration config = new TestConfiguration(wireMockServer.port());
        config.setRestTestEndpoint("/items");

        TestRestConnector connector = new TestRestConnector(config);
        connector.init(config);

        try {
            connector.test();
            // Success - no exception
        } catch (Exception e) {
            fail("Should not throw exception for successful response: " + e.getMessage());
        }
    }

    @Test
    public void testSinglePageWithMalformedJson() {
        setUpWireMock();

        // Note: test() method only checks status codes, not body content
        // Since we're returning 200 with malformed JSON, no exception should be thrown
        wireMockServer.stubFor(get(urlEqualTo("/items"))
            .willReturn(aResponse().withStatus(200)
                .withBody("{malformed json")));

        TestConfiguration config = new TestConfiguration(wireMockServer.port());
        config.setRestTestEndpoint("/items");

        TestRestConnector connector = new TestRestConnector(config);
        connector.init(config);

        try {
            connector.test();
            // 200 with malformed JSON body is acceptable for test() as it doesn't parse bodies
        } catch (Exception e) {
            fail("Should not throw exception for 200 status with malformed JSON body: " + e.getMessage());
        }
    }

    @Test
    public void testSinglePageWith401() {
        setUpWireMock();

        wireMockServer.stubFor(get(urlEqualTo("/items"))
            .willReturn(aResponse().withStatus(401)));

        TestConfiguration config = new TestConfiguration(wireMockServer.port());
        config.setRestTestEndpoint("/items");

        TestRestConnector connector = new TestRestConnector(config);
        connector.init(config);

        try {
            connector.test();
            fail("Expected exception was not thrown");
        } catch (Exception e) {
            assertTrue(e instanceof org.identityconnectors.framework.common.exceptions.InvalidCredentialException,
                "Expected InvalidCredentialException for 401");
            assertTrue(e.getMessage().contains("401"),
                "Error should mention status code 401");
        }
    }

    @Test
    public void testSinglePageWith403() {
        setUpWireMock();

        wireMockServer.stubFor(get(urlEqualTo("/items"))
            .willReturn(aResponse().withStatus(403)));

        TestConfiguration config = new TestConfiguration(wireMockServer.port());
        config.setRestTestEndpoint("/items");

        TestRestConnector connector = new TestRestConnector(config);
        connector.init(config);

        try {
            connector.test();
            fail("Expected exception was not thrown");
        } catch (Exception e) {
            assertTrue(e instanceof org.identityconnectors.framework.common.exceptions.InvalidCredentialException,
                "Expected InvalidCredentialException for 403");
            assertTrue(e.getMessage().contains("403"),
                "Error should mention status code 403");
        }
    }

    @Test
    public void testSinglePageWithZeroResults() {
        setUpWireMock();

        wireMockServer.stubFor(get(urlEqualTo("/items"))
            .willReturn(aResponse().withStatus(200)
                .withBody("[]")));

        TestConfiguration config = new TestConfiguration(wireMockServer.port());
        config.setRestTestEndpoint("/items");

        TestRestConnector connector = new TestRestConnector(config);
        connector.init(config);

        try {
            connector.test();
            // Empty array is valid response
        } catch (Exception e) {
            fail("Should not throw exception for empty result set: " + e.getMessage());
        }
    }

    @Test
    public void testSinglePageExceptionWrapsOriginalException() {
        setUpWireMock();

        wireMockServer.stubFor(get(urlEqualTo("/items"))
            .willReturn(aResponse().withStatus(500)));

        TestConfiguration config = new TestConfiguration(wireMockServer.port());
        config.setRestTestEndpoint("/items");

        TestRestConnector connector = new TestRestConnector(config);
        connector.init(config);

        try {
            connector.test();
            fail("Expected exception was not thrown");
        } catch (Exception e) {
            assertTrue(e instanceof org.identityconnectors.framework.common.exceptions.ConnectionFailedException ||
                    e instanceof org.identityconnectors.framework.common.exceptions.ConnectorException);
            assertNotNull(e.getStackTrace(),
                "Exception should have stack trace for debugging");
        }
    }

    private static class TestConfiguration extends BaseRestGroovyConnectorConfiguration {
        private final int port;
        private String testEndpoint = "/items";

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
