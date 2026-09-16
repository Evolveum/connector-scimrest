/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.rest;

import org.identityconnectors.framework.common.exceptions.ConnectionBrokenException;
import org.identityconnectors.framework.common.exceptions.ConnectionFailedException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.ConnectorIOException;
import org.identityconnectors.framework.common.exceptions.OperationTimeoutException;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.channels.ClosedChannelException;

/**
 * Maps raw JDK {@code java.net.http} client failures to the ICF exception types that midPoint
 * reacts to correctly: timeouts and connection problems become retryable (task postponed,
 * resource marked {@code DOWN}) instead of unclassified {@code ConnectorException}s (task
 * suspended).
 *
 * <p>Every message states what failed and where (the target URI), and the original exception
 * is always kept as the cause. Messages are guarded against {@code null} cause messages
 * (falling back to the cause class name) so errors never read {@code "...: null"}.</p>
 */
public final class HttpExceptionMapper {

    private HttpExceptionMapper() {
    }

    /**
     * Maps the given {@link IOException} (as thrown by the JDK HTTP client) to an ICF
     * exception with a message naming the target URI.
     *
     * @param e   the I/O failure, never {@code null}
     * @param uri the target URI the request was sent to (for the message)
     * @return the mapped ICF exception
     */
    public static RuntimeException map(IOException e, String uri) {
        if (e instanceof HttpConnectTimeoutException t) {
            return new OperationTimeoutException("Connection to " + uri + " timed out", t);
        }
        if (e instanceof HttpTimeoutException t) {
            return new OperationTimeoutException("REST request to " + uri + " timed out", t);
        }
        if (e instanceof ClosedChannelException t) {
            return new ConnectionBrokenException("Connection to " + uri + " was closed before the request completed", t);
        }
        if (e instanceof UnknownHostException t) {
            return new ConnectionFailedException("Could not resolve host for " + uri + ": " + causeMessage(t), t);
        }
        if (e instanceof ConnectException t) {
            return new ConnectionFailedException("Could not connect to " + uri + ": " + causeMessage(t), t);
        }
        if (e instanceof SSLException t) {
            return new ConnectionFailedException("TLS handshake with " + uri + " failed: " + causeMessage(t), t);
        }
        if (e instanceof SocketException t) {
            String msg = causeMessage(t);
            if (msg.contains("reset") || msg.contains("Broken pipe")) {
                return new ConnectionBrokenException("Connection to " + uri + " was reset: " + msg, t);
            }
            return new ConnectionFailedException("Could not connect to " + uri + ": " + msg, t);
        }
        return new ConnectorIOException("I/O error while communicating with " + uri + ": " + causeMessage(e), e);
    }

    /**
     * Maps an {@link InterruptedException} (as thrown by the JDK HTTP client) to an ICF
     * exception, restoring the current thread's interrupt flag.
     *
     * @param e   the interrupt, never {@code null}
     * @param uri the target URI the request was sent to (for the message)
     * @return the mapped ICF exception
     */
    public static RuntimeException map(InterruptedException e, String uri) {
        Thread.currentThread().interrupt();
        return new ConnectionBrokenException("REST request to " + uri + " was interrupted", e);
    }

    /**
     * Returns a non-null, non-empty description of the given exception's message: the message
     * itself, or the exception class name when the message is {@code null} or blank.
     */
    public static String causeMessage(Throwable e) {
        String msg = e.getMessage();
        return (msg == null || msg.isBlank()) ? e.getClass().getSimpleName() : msg;
    }

    /**
     * The JDK HTTP client wraps any exception thrown from a {@link HttpResponse.BodyHandler}
     * (e.g. a JSON parse error surfaced by {@code JacksonBodyHandler}) into an
     * {@link IOException}. That would mislabel a definitive parse/configuration error as a
     * transient I/O failure — midPoint would keep retrying a body that never parses.
     *
     * @param e the (possibly JDK-wrapped) exception
     * @return the ICF exception found in the cause chain, or {@code null} when the failure is a
     *         genuine I/O problem that should be mapped normally
     */
    public static ConnectorException unwrapIcf(Throwable e) {
        for (Throwable t = e; t != null; t = (t.getCause() == t) ? null : t.getCause()) {
            if (t instanceof ConnectorException icf) {
                return icf;
            }
        }
        return null;
    }
}
