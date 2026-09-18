/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.exception;

import com.evolveum.polygon.scimrest.impl.rest.HttpExceptionMapper;
import com.evolveum.polygon.scimrest.support.WireMockTestSupport;
import com.evolveum.polygon.scimrest.support.TestRestConnector;
import org.identityconnectors.framework.common.exceptions.*;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tools.jackson.core.JacksonException;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * End-to-end verification that the connector boundary maps raw failures onto the ICF
 * exception types midPoint reacts to correctly (retry, DOWN, not "task suspended").
 */
public class ExceptionHandlingTest extends WireMockTestSupport {

    private static final String SCHEMA_SCRIPT = """
            objectClass("Account") { }
            """;

    private static final String OPERATION_SCRIPT = """
            objectClass("Account") {
                search {
                    endpoint("accounts") {
                        emptyFilterSupported true
                    }
                }
            }
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
    public void test401BecomesInvalidCredential() {
        wireMockServer.stubFor(get(urlEqualTo("/test"))
            .willReturn(aResponse().withStatus(401)));

        var config = new TestConfiguration(wireMockServer.port());
        config.setRestTestEndpoint("/test");
        var connector = new TestRestConnector(config);
        connector.init(config);

        var e = Assert.expectThrows(InvalidCredentialException.class, connector::test);
        assertTrue(e.getMessage().contains("401"),
                "Message should carry the HTTP status: " + e.getMessage());
    }

    @Test
    public void test403BecomesInvalidCredential() {
        wireMockServer.stubFor(get(urlEqualTo("/test"))
            .willReturn(aResponse().withStatus(403)));

        var config = new TestConfiguration(wireMockServer.port());
        config.setRestTestEndpoint("/test");
        var connector = new TestRestConnector(config);
        connector.init(config);

        var e = Assert.expectThrows(InvalidCredentialException.class, connector::test);
        assertTrue(e.getMessage().contains("403"),
                "Message should carry the HTTP status: " + e.getMessage());
    }

    @Test
    public void test500StatusCodeBecomesConnectionFailed() {
        wireMockServer.stubFor(get(urlEqualTo("/test"))
            .willReturn(aResponse().withStatus(500)));

        var config = new TestConfiguration(wireMockServer.port());
        config.setRestTestEndpoint("/test");
        var connector = new TestRestConnector(config);
        connector.init(config);

        var e = Assert.expectThrows(ConnectionFailedException.class, connector::test);
        assertTrue(e.getMessage().contains("500"),
                "Message should carry the HTTP status: " + e.getMessage());
    }

    @Test
    public void testJsonParsingErrorIsUserFriendly() {
        wireMockServer.stubFor(get(urlEqualTo("/accounts"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("invalid json { broken")));

        var config = new TestConfiguration(wireMockServer.port());
        var connector = new ScriptConnector(null, SCHEMA_SCRIPT, OPERATION_SCRIPT);
        connector.init(config);

        var e = Assert.expectThrows(ConnectorException.class,
                () -> connector.executeQuery(new ObjectClass("Account"), null, r -> true,
                        new OperationOptionsBuilder().build()));
        assertTrue(e.getClass() == ConnectorException.class,
                "A malformed body is a parse error (ConnectorException), not a more specific ICF type: "
                        + e.getClass().getName());
        assertTrue(e.getMessage().contains("Failed to parse response body"),
                "Parse error should name the problem: " + e.getMessage());
        assertFalse(e instanceof ConnectorIOException,
                "A malformed body is not an I/O failure");
        assertTrue(e.getCause() instanceof JacksonException,
                "The Jackson error must be kept as the cause");
    }

    @Test
    public void testInterruptedExceptionBecomesConnectionBroken() {
        // Interrupted requests map to ConnectionBrokenException and restore the flag.
        var e = HttpExceptionMapper.map(new InterruptedException("interrupted"), "http://localhost:1/test");
        assertTrue(e instanceof ConnectionBrokenException,
                "Expected ConnectionBrokenException but got: " + e.getClass().getName());
        assertTrue(Thread.currentThread().isInterrupted(),
                "The interrupt flag must be restored");
        Thread.interrupted(); // clear the flag set by the mapper under test
        assertTrue(e.getCause() instanceof InterruptedException, "The interrupt must be the cause");
    }

    @Test
    public void testInvalidBaseUriBecomesConfigurationException() {
        // A base address that cannot form a valid URI (space) is a configuration error, not
        // a transient connection failure.
        var config = new TestConfiguration(wireMockServer.port());
        config.setBaseAddress("http://bad address with spaces.example.com");
        config.setRestTestEndpoint("/test");
        var connector = new TestRestConnector(config);
        connector.init(config);

        var e = Assert.expectThrows(ConfigurationException.class, connector::test);
        assertTrue(e.getMessage().contains("URI"),
                "Message should say the URI is the problem: " + e.getMessage());
    }

    private static class TestConfiguration extends BaseTestConfiguration {
        TestConfiguration(int port) {
            super(port);
        }
    }
}
