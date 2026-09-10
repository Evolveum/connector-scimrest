/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.logging;

import com.evolveum.polygon.conndev.logging.ConnDevLog;
import com.evolveum.polygon.conndev.logging.protocol.HttpProtocolData;

import java.nio.charset.StandardCharsets;

/**
 * Attaches HTTP protocol events to the operation entry currently active on this thread (started
 * by {@code ConnectorLog.runOperation} in {@code ClassHandlerConnectorBase}).
 *
 * <p>The events are emitted through the facade's own logger, so the protocol layer can be
 * filtered and leveled independently of the operation handlers, while the shared entry id and
 * sequence numbers keep them correlated with the enclosing operation. When no operation entry
 * is active (e.g. calls outside of a ConnId operation), or when development mode is disabled,
 * the calls are no-ops.
 */
public final class ProtocolTrace {

    private ProtocolTrace() {
    }

    /**
     * Logs an outgoing HTTP request on the active operation entry, if any.
     *
     * @param log    the logging facade bound to the emitting component
     * @param method the HTTP method
     * @param uri    the target URI
     * @param body   the request body (may be null; raw bytes are converted to text)
     */
    public static void request(ConnDevLog log, String method, String uri, Object body) {
        var entry = log.currentOperation();
        if (entry != null) {
            entry.http(new HttpProtocolData.Request(method, uri, normalize(body)));
        }
    }

    /**
     * Logs a received HTTP response on the active operation entry, if any.
     *
     * @param log    the logging facade bound to the emitting component
     * @param status the HTTP status code
     * @param uri    the target URI
     * @param body   the response body (may be null; raw bytes are converted to text)
     */
    public static void response(ConnDevLog log, int status, String uri, Object body) {
        var entry = log.currentOperation();
        if (entry != null) {
            entry.http(new HttpProtocolData.Response(status, uri, normalize(body)));
        }
    }

    private static Object normalize(Object body) {
        if (body instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return body;
    }
}
