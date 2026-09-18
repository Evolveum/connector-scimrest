/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.scim;

import com.evolveum.polygon.scimrest.impl.rest.HttpExceptionMapper;
import com.evolveum.polygon.scimrest.impl.rest.HttpStatusMapper;
import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.identityconnectors.framework.common.exceptions.ConnectionBrokenException;
import org.identityconnectors.framework.common.exceptions.ConnectionFailedException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.ConnectorIOException;
import org.identityconnectors.framework.common.exceptions.OperationTimeoutException;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.channels.ClosedChannelException;

/**
 * Maps SCIM-stack failures to the ICF exception types that midPoint reacts to correctly.
 *
 * <p>Two kinds of failures are mapped:</p>
 * <ul>
 *   <li><b>HTTP errors</b> ({@link ScimHttpErrorException}, thrown by {@link ScimHttpErrorFilter}):
 *       the status is translated per operation kind via {@link HttpStatusMapper} — 404 becomes
 *       {@code UnknownUidException} for a retrieve/update/delete but {@code ConfigurationException}
 *       for a search, 409 becomes {@code AlreadyExistsException}, 401/403 become
 *       {@code InvalidCredentialException}/{@code PermissionDeniedException}, and the server's
 *       RFC 7644 {@code detail} is carried into the message.</li>
 *   <li><b>Network failures</b> (Apache HttpClient 5 causes, which use different exception
 *       classes than the JDK HTTP client): timeouts become {@code OperationTimeoutException},
 *       connection problems become {@code ConnectionFailedException}/{@code ConnectionBrokenException}
 *       so midPoint postpones the operation and marks the resource {@code DOWN} instead of
 *       suspending the task.</li>
 * </ul>
 *
 * <p>ICF exceptions produced at the boundary (by this class or {@code ScimHttpErrorFilter}) are
 * never wrapped again: handler catch blocks rethrow {@code ConnectorException}s untouched.</p>
 */
public final class ScimExceptionMapper {

    private ScimExceptionMapper() {
    }

    /**
     * Translates a SCIM HTTP error to the ICF exception for the given operation kind.
     *
     * @param e    the HTTP error, never {@code null}
     * @param kind the operation kind (drives the 404 mapping)
     * @param uid  the object UID the operation targeted, or {@code null}
     * @return the mapped ICF exception
     */
    public static RuntimeException map(ScimHttpErrorException e, HttpStatusMapper.OperationKind kind, String uid) {
        String uri = e.requestUri() != null ? e.requestUri().toString() : null;
        return HttpStatusMapper.map(e.status(), kind, uri, uid, e.detail());
    }

    /**
     * Maps a network-layer failure (as thrown while executing a SCIM request via Apache
     * HttpClient 5) to an ICF exception, walking the cause chain for the underlying
     * {@link IOException}/{@link InterruptedException}.
     *
     * @param e   the failure, never {@code null}
     * @param uri the target URI the request was sent to (for the message)
     * @return the mapped ICF exception
     */
    public static RuntimeException mapFailure(Throwable e, String uri) {
        // An ICF exception (e.g. ScimHttpErrorException) already carries the right type and
        // message — never wrap it again.
        if (e instanceof ConnectorException icf) {
            return icf;
        }
        Throwable t = e;
        while (t != null) {
            if (t instanceof InterruptedException i) {
                return mapInterrupted(i, uri);
            }
            if (t instanceof IOException io) {
                return mapNetwork(io, uri);
            }
            t = t.getCause();
        }
        return new ConnectorException("SCIM request to " + uri + " failed: " + HttpExceptionMapper.causeMessage(e), e);
    }

    /**
     * Maps an Apache HttpClient 5 (or JDK) {@link IOException} to an ICF exception with a
     * message naming the target URI.
     */
    public static RuntimeException mapNetwork(IOException e, String uri) {
        if (e instanceof ConnectTimeoutException t) {
            return new OperationTimeoutException("Connection to " + uri + " timed out", t);
        }
        if (e instanceof SocketTimeoutException t) {
            return new OperationTimeoutException("SCIM request to " + uri + " timed out", t);
        }
        if (e instanceof ConnectionClosedException t) {
            return new ConnectionBrokenException("Connection to " + uri + " was closed before the request completed", t);
        }
        if (e instanceof InterruptedIOException t) {
            return mapInterrupted(t, uri);
        }
        if (e instanceof SSLException t) {
            return new ConnectionFailedException("TLS handshake with " + uri + " failed: " + HttpExceptionMapper.causeMessage(t), t);
        }
        if (e instanceof UnknownHostException t) {
            return new ConnectionFailedException("Could not resolve host for " + uri + ": " + HttpExceptionMapper.causeMessage(t), t);
        }
        if (e instanceof ConnectException t) {
            return new ConnectionFailedException("Could not connect to " + uri + ": " + HttpExceptionMapper.causeMessage(t), t);
        }
        if (e instanceof ClosedChannelException t) {
            return new ConnectionBrokenException("Connection to " + uri + " was closed before the request completed", t);
        }
        return new ConnectorIOException("I/O error while communicating with " + uri + ": " + HttpExceptionMapper.causeMessage(e), e);
    }

    private static RuntimeException mapInterrupted(Throwable e, String uri) {
        Thread.currentThread().interrupt();
        return new ConnectionBrokenException("SCIM request to " + uri + " was interrupted", e);
    }
}
