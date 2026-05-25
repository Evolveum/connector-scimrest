/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.exception;

import com.evolveum.polygon.scimrest.config.ScimClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.*;
import org.identityconnectors.common.security.GuardedString;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.*;

public class ScimExceptionSimulationTest extends WireMockTestSupport {

    @AfterMethod
    public void tearDown() {
        tearDownWireMock();
    }

    @Test
    public void test401UnauthorizedNoJsonbException() {
        setUpWireMock();

        wireMockServer.stubFor(get(urlPathMatching("/scim/v2/.*"))
            .willReturn(aResponse().withStatus(401)));

        TestConfiguration config = new TestConfiguration(wireMockServer.port());
        TestScimConnector connector = new TestScimConnector(config);
        connector.init(config);

        try {
            connector.schema();
            fail("Expected exception was not thrown for SCIM 401");
        } catch (Exception e) {
            assertTrue(e.getMessage() != null && containsStatusCode(e, "401"),
                "Exception chain should reference HTTP 401, got: " + e.getMessage());
            assertNoJsonbException(e);
        }
    }

    private boolean containsStatusCode(Throwable e, String code) {
        if (e == null) return false;
        if (e.getMessage() != null && e.getMessage().contains(code)) return true;
        return containsStatusCode(e.getCause(), code);
    }

    private void assertNoJsonbException(Throwable e) {
        if (e == null) return;
        assertFalse(e.getClass().getName().contains("JsonbException"),
            "JsonbException should not appear in the cause chain");
        assertNoJsonbException(e.getCause());
    }

    @Test
    public void testScimSearchFailure() {
        setUpWireMock();

        wireMockServer.stubFor(get(urlEqualTo("/scim/v2/Users"))
            .willReturn(aResponse().withStatus(500)));

        TestConfiguration config = new TestConfiguration(wireMockServer.port());
        TestScimConnector connector = new TestScimConnector(config);
        connector.init(config);

        try {
            connector.schema();
            fail("Expected exception was not thrown for SCIM search failure");
        } catch (Exception e) {
            assertNotNull(e);
        }
    }

    @Test
    public void testScimRetrieveFailure() {
        setUpWireMock();

        wireMockServer.stubFor(get(urlEqualTo("/scim/v2/Users/*"))
            .willReturn(aResponse().withStatus(404)));

        TestConfiguration config = new TestConfiguration(wireMockServer.port());
        TestScimConnector connector = new TestScimConnector(config);
        connector.init(config);

        try {
            connector.schema();
            fail("Expected exception was not thrown for SCIM retrieve failure");
        } catch (Exception e) {
            assertNotNull(e);
        }
    }

    // Test configuration
    private static class TestConfiguration extends BaseRestGroovyConnectorConfiguration implements ScimClientConfiguration.BearerTokenAuthorization {
        private final int port;
        private String scimBaseUrl;
        private String token = "test-token";

        TestConfiguration(int port) {
            this.port = port;
            this.scimBaseUrl = "http://localhost:" + port + "/scim/v2";
        }

        @Override
        public String getBaseAddress() {
            return "http://localhost:" + port;
        }

        @Override
        public String getScimBaseUrl() {
            return scimBaseUrl;
        }

        @Override
        public GuardedString getScimTokenValue() {
            return new GuardedString(token.toCharArray());
        }
    }

    // Test connector with SCIM enabled
    private static class TestScimConnector extends AbstractGroovyRestConnector<TestConfiguration> {
        private final TestConfiguration configuration;

        TestScimConnector(TestConfiguration configuration) {
            this.configuration = configuration;
        }

        @Override
        protected void initializeSchema(GroovySchemaLoader loader) {
            // Empty schema for testing
        }

        @Override
        protected void initializeAuthorizationHandler(GroovyRestHandlerBuilder builder) {}

        @Override
        protected void initializeObjectClassHandler(GroovyRestHandlerBuilder builder) {
            // No handlers needed for test
        }
    }
}
