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
 * Tests for HTTP 4xx status code error handling
 */
public class HttpStatus4xxTests extends WireMockTestSupport {

    @AfterMethod
    public void tearDown() {
        tearDownWireMock();
    }

    @Test
    public void test400BadRequestBecomesConnectionFailed() {
        setUpWireMock();

        wireMockServer.stubFor(get(urlEqualTo("/test"))
            .willReturn(aResponse().withStatus(400)));

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
            assertTrue(e.getMessage().contains("400"),
                "Error message should contain status code 400");
        }
    }

    @Test
    public void test401UnauthorizedBecomesInvalidCredential() {
        setUpWireMock();

        wireMockServer.stubFor(get(urlEqualTo("/test"))
            .willReturn(aResponse().withStatus(401)));

        TestConfiguration config = new TestConfiguration(wireMockServer.port());
        config.setRestTestEndpoint("/test");

        TestRestConnector connector = new TestRestConnector(config);
        connector.init(config);

        try {
            connector.test();
            fail("Expected InvalidCredentialException was not thrown");
        } catch (Exception e) {
            assertTrue(e instanceof org.identityconnectors.framework.common.exceptions.InvalidCredentialException,
                "Expected InvalidCredentialException but got: " + e.getClass().getName());
            assertTrue(e.getMessage().contains("401"),
                "Error message should contain status code 401");
        }
    }

    @Test
    public void test403ForbiddenBecomesInvalidCredential() {
        setUpWireMock();

        wireMockServer.stubFor(get(urlEqualTo("/test"))
            .willReturn(aResponse().withStatus(403)));

        TestConfiguration config = new TestConfiguration(wireMockServer.port());
        config.setRestTestEndpoint("/test");

        TestRestConnector connector = new TestRestConnector(config);
        connector.init(config);

        try {
            connector.test();
            fail("Expected InvalidCredentialException was not thrown");
        } catch (Exception e) {
            assertTrue(e instanceof org.identityconnectors.framework.common.exceptions.InvalidCredentialException,
                "Expected InvalidCredentialException but got: " + e.getClass().getName());
            assertTrue(e.getMessage().contains("403"),
                "Error message should contain status code 403");
        }
    }

    @Test
    public void test404NotFoundBecomesConnectorException() {
        setUpWireMock();

        wireMockServer.stubFor(get(urlEqualTo("/test"))
            .willReturn(aResponse().withStatus(404)));

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
            assertTrue(e.getMessage().contains("404"),
                "Error message should contain status code 404");
        }
    }

    @Test
    public void test409ConflictBecomesConnectorException() {
        setUpWireMock();

        wireMockServer.stubFor(get(urlEqualTo("/test"))
            .willReturn(aResponse().withStatus(409)));

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
            assertTrue(e.getMessage().contains("409"),
                "Error message should contain status code 409");
        }
    }

    @Test
    public void test422UnprocessableEntityBecomesConnectorException() {
        setUpWireMock();

        wireMockServer.stubFor(get(urlEqualTo("/test"))
            .willReturn(aResponse().withStatus(422)));

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
            assertTrue(e.getMessage().contains("422"),
                "Error message should contain status code 422");
        }
    }

    // Test configuration
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
