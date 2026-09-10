/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.crud;

import com.evolveum.polygon.conndev.devtools.log.ConndevLogFormat;
import com.evolveum.polygon.conndev.devtools.log.LogSeverity;
import com.evolveum.polygon.conndev.devtools.log.OperationLogParser;
import com.evolveum.polygon.conndev.devtools.log.OperationTrace;
import com.evolveum.polygon.scimrest.logging.CapturingLogProvider;
import com.evolveum.polygon.scimrest.support.AbstractCrudConnectorTest;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Verifies that the protocol layer (the detached REST client) emits HTTP protocol events that
 * correlate with the operation entry of the enclosing ConnId operation, on the protocol
 * component's own logger.
 */
public class ProtocolTracingTest extends AbstractCrudConnectorTest {

    private static final String REST_CONTEXT_LOGGER = "com.evolveum.polygon.scimrest.impl.rest.RestContext";

    @Test
    public void updateEmitsProtocolEventsCorrelatedWithOperationInDevelopmentMode() {
        stubUpdateAccount();
        var connector = initConnectorWithDevelopmentMode();
        CapturingLogProvider.clear();
        updateAccount(connector);

        var lines = CapturingLogProvider.lines();
        assertTrue(lines.stream().anyMatch(line -> line.logger().equals(REST_CONTEXT_LOGGER)
                        && line.message().contains(ConndevLogFormat.MARKER)),
                "expected a structured line emitted by the RestContext logger");

        var traces = OperationLogParser.parse(lines.stream()
                .map(CapturingLogProvider.CapturedLine::message).toList());
        assertEquals(traces.size(), 1);
        var trace = traces.get(0);
        assertEquals(trace.operation(), "update");
        assertEquals(trace.objectClass(), "Account");
        assertTrue(trace.completed());
        assertTrue(trace.outcome().ok());

        var requests = trace.protocolEvents().stream()
                .filter(event -> "request".equals(event.protocol().kind()))
                .toList();
        assertEquals(requests.size(), 2);
        assertTrue(requests.stream().anyMatch(event -> "PUT".equals(event.protocol().method())
                && event.protocol().uri().endsWith("/accounts/123")));
        assertTrue(requests.stream().anyMatch(event -> "GET".equals(event.protocol().method())
                && event.protocol().uri().endsWith("/accounts/123")));
        assertTrue(requests.stream().allMatch(event -> event.protocol().body() == null));
        assertTrue(requests.stream().allMatch(event -> event.severity() == LogSeverity.DEBUG));

        var responses = trace.protocolEvents().stream()
                .filter(event -> "response".equals(event.protocol().kind()))
                .toList();
        assertEquals(responses.size(), 2);
        assertTrue(responses.stream().allMatch(event -> event.protocol().status() == 200));
        assertTrue(responses.stream().allMatch(event -> event.protocol().body() == null));

        var requestBodies = trace.protocolEvents().stream()
                .filter(event -> "request-body".equals(event.protocol().kind()))
                .toList();
        assertEquals(requestBodies.size(), 1);
        assertEquals(requestBodies.get(0).severity(), LogSeverity.TRACE);
        assertTrue(requestBodies.get(0).protocol().uri().endsWith("/accounts/123"));
        assertTrue(requestBodies.get(0).protocol().body().contains("updated"));

        var responseBodies = trace.protocolEvents().stream()
                .filter(event -> "response-body".equals(event.protocol().kind()))
                .toList();
        assertEquals(responseBodies.size(), 2);
        assertTrue(responseBodies.stream().allMatch(event -> event.severity() == LogSeverity.TRACE));

        // the handler-level orchestration details are part of the same trace
        assertTrue(trace.details().stream().anyMatch(detail -> detail.detail().containsKey("routing")));
        assertTrue(trace.details().stream().anyMatch(detail -> detail.detail().containsKey("executing")));
    }

    @Test
    public void updateEmitsNoStructuredLinesWithoutDevelopmentMode() {
        stubUpdateAccount();
        var connector = initConnector(OPERATION_SCRIPT);
        CapturingLogProvider.clear();
        updateAccount(connector);

        assertTrue(CapturingLogProvider.lines().stream()
                .noneMatch(line -> line.message().contains(ConndevLogFormat.MARKER)),
                "expected no structured log lines without development mode");
    }

    private TestConnector initConnectorWithDevelopmentMode() {
        var connector = new TestConnector();
        connector.init(new DevelopmentConfig(wireMockServer.port()));
        return connector;
    }

    private static final class DevelopmentConfig extends BaseTestConfiguration {

        private DevelopmentConfig(int port) {
            super(port);
            setDevelopmentMode(true);
        }

        @Override
        public String getRestTestEndpoint() {
            return null;
        }
    }
}
