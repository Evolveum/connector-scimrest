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

/**
 * Tests for authentication error handling (Basic, Bearer token, API key)
 */
public class AuthenticationErrorTests extends WireMockTestSupport {

    @AfterMethod
    public void tearDown() {
        tearDownWireMock();
    }

    @Test
    public void testBasicAuthInvalidCredentialsBecomes401() {
        setUpWireMock();

        wireMockServer.stubFor(get(urlEqualTo("/test"))
            .willReturn(aResponse().withStatus(401)));

        TestConfiguration config = new TestConfiguration(wireMockServer.port());
        config.setRestTestEndpoint("/test");
        config.setRestUsername("wronguser");
        config.setRestPassword(new GuardedString("wrongpass".toCharArray()));
        
        System.err.println("WireMock port: " + wireMockServer.port());
        System.err.println("Config base address: " + config.getBaseAddress());
        System.err.println("Config test endpoint: " + config.getRestTestEndpoint());

        TestRestConnector connector = new TestRestConnector(config);
        connector.init(config);

        try {

            connector.test();
            fail("Expected InvalidCredentialException was not thrown");
        } catch (Exception e) {
            System.err.println("Exception type: " + e.getClass().getName());
            System.err.println("Exception message: " + e.getMessage());
            
            // Check what request was made
            int requestCount = wireMockServer.findAll(getRequestedFor(urlEqualTo("/test"))).size();
            System.err.println("Request count for /test: " + requestCount);
            
            // List all urls that were requested
            wireMockServer.getAllServeEvents().stream()
                .map(event -> event.getRequest().getUrl())
                .forEach(url -> System.err.println("Request URL: " + url));
            
            // Also check requests for any path
            int anyRequestCount = wireMockServer.findAll(anyRequestedFor(urlMatching(".*"))).size();
            System.err.println("Total requests to WireMock: " + anyRequestCount);
            
            assertTrue(e instanceof org.identityconnectors.framework.common.exceptions.InvalidCredentialException,
                "Expected InvalidCredentialException but got: " + e.getClass().getName());
            assertTrue(e.getMessage().contains("401"),
                "Error message should contain status code 401");
        }
    }

    @Test
    public void testBearerTokenInvalidTokenBecomes401() {
        setUpWireMock();

        wireMockServer.stubFor(get(urlEqualTo("/test"))
            .willReturn(aResponse().withStatus(401)));

        TestConfiguration config = new TestConfiguration(wireMockServer.port());
        config.setRestTestEndpoint("/test");
        config.tokenValue = new GuardedString("invalid-token".toCharArray());

        TestRestConnector connector = new TestRestConnector(config);
        connector.init(config);

        try {
            connector.test();
            fail("Expected InvalidCredentialException was not thrown");
        } catch (Exception e) {
            assertTrue(e instanceof org.identityconnectors.framework.common.exceptions.InvalidCredentialException,
                "Expected InvalidCredentialException but got: " + e.getClass().getName());
        }
    }

    @Test
    public void testApiKeyInvalidKeyBecomes403() {
        setUpWireMock();

        wireMockServer.stubFor(get(urlEqualTo("/test"))
            .willReturn(aResponse().withStatus(403)));

        TestConfiguration config = new TestConfiguration(wireMockServer.port());
        config.setRestTestEndpoint("/test");
        config.apiKey = new GuardedString("invalid-api-key".toCharArray());

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
    public void testBasicAuthMissingUsername() {
        setUpWireMock();

        wireMockServer.stubFor(get(urlEqualTo("/test"))
            .willReturn(aResponse().withStatus(401)));

        TestConfiguration config = new TestConfiguration(wireMockServer.port());
        config.setRestTestEndpoint("/test");
        config.setRestUsername("");
        config.setRestPassword(new GuardedString("password".toCharArray()));

        TestRestConnector connector = new TestRestConnector(config);
        connector.init(config);

        try {
            connector.test();
            fail("Expected exception was not thrown");
        } catch (Exception e) {
            assertTrue(e instanceof org.identityconnectors.framework.common.exceptions.ConnectionFailedException ||
                    e instanceof org.identityconnectors.framework.common.exceptions.InvalidCredentialException);
        }
    }

    @Test
    public void testTokenAuthWithExpiredToken() {
        setUpWireMock();

        wireMockServer.stubFor(get(urlEqualTo("/test"))
            .willReturn(aResponse().withStatus(401)));

        TestConfiguration config = new TestConfiguration(wireMockServer.port());
        config.setRestTestEndpoint("/test");
        config.tokenValue = new GuardedString("expired-token".toCharArray());

        TestRestConnector connector = new TestRestConnector(config);
        connector.init(config);

        try {
            connector.test();
            fail("Expected InvalidCredentialException was not thrown");
        } catch (Exception e) {
            assertTrue(e instanceof org.identityconnectors.framework.common.exceptions.InvalidCredentialException);
        }
    }

    @Test
    public void testMissingAuthorizationHeader() {
        setUpWireMock();

        wireMockServer.stubFor(get(urlEqualTo("/test"))
            .willReturn(aResponse().withStatus(401)));

        TestConfiguration config = new TestConfiguration(wireMockServer.port());
        config.setRestTestEndpoint("/test");

        TestRestConnector connector = new TestRestConnector(config);
        connector.init(config);

        try {
            connector.test();
            fail("Expected exception was not thrown");
        } catch (Exception e) {
            assertTrue(e instanceof org.identityconnectors.framework.common.exceptions.ConnectionFailedException ||
                    e instanceof org.identityconnectors.framework.common.exceptions.InvalidCredentialException);
        }
    }

    @Test
    public void testAuthenticationErrorDoesNotLeakCredentials() {
        setUpWireMock();

        wireMockServer.stubFor(get(urlEqualTo("/secure-test"))
            .willReturn(aResponse().withStatus(401).withBody("Authentication required")));

        TestConfiguration config = new TestConfiguration(wireMockServer.port());
        config.setRestTestEndpoint("/secure-test");
        config.setRestUsername("testuser");
        config.setRestPassword(new GuardedString("secret-password".toCharArray()));

        TestRestConnector connector = new TestRestConnector(config);
        connector.init(config);

        try {
            connector.test();
            fail("Expected exception was not thrown");
        } catch (Exception e) {
            assertTrue(e instanceof org.identityconnectors.framework.common.exceptions.InvalidCredentialException);
            assertFalse(e.getMessage().contains("secret-password"),
                "Error message should not contain password");
            assertFalse(e.getMessage().contains("testuser"),
                "Error message should not contain username");
        }
    }

    private static class TestConfiguration extends BaseRestGroovyConnectorConfiguration
            implements RestClientConfiguration.BasicAuthorization,
                       RestClientConfiguration.TokenAuthorization,
                       RestClientConfiguration.ApiKeyAuthorization {
        private final int port;
        private String baseAddress = "http://localhost";
        private String testEndpoint = "/test";
        private String username;
        private GuardedString password;
        private GuardedString tokenValue;
        private GuardedString apiKey;

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

        @Override
        public String getRestTokenName() {
            return "Authorization";
        }

        @Override
        public GuardedString getRestTokenValue() {
            return tokenValue;
        }

        @Override
        public GuardedString getRestApiKey() {
            return apiKey;
        }

        public void setApiKey(GuardedString apiKey) {
            this.apiKey = apiKey;
        }
    }
}
