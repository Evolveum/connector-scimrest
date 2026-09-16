/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.rest;

import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.exceptions.ConnectionFailedException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.InvalidCredentialException;
import org.identityconnectors.framework.common.exceptions.PermissionDeniedException;
import org.identityconnectors.framework.common.exceptions.RetryableException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;

/**
 * Maps HTTP response status codes to the ICF exception types that midPoint reacts to
 * correctly, taking into account which operation produced the response.
 *
 * <p>The mapping (per the midPoint/ConnId contract):</p>
 * <ul>
 *   <li>401 — {@link InvalidCredentialException} (retried login, resource {@code DOWN})</li>
 *   <li>403 — {@link PermissionDeniedException} (hard security failure)</li>
 *   <li>404 — {@link UnknownUidException} for get/update/delete (tombstone + idempotent delete);
 *       {@link ConfigurationException} for search (the endpoint path is misconfigured)</li>
 *   <li>409 — {@link AlreadyExistsException} (conflicting-object discovery)</li>
 *   <li>429 — {@link RetryableException} (rate limited, retryable)</li>
 *   <li>5xx — {@link ConnectionFailedException} (server-side problem, transient)</li>
 *   <li>other 4xx — {@link ConnectorException} (retries will not help)</li>
 * </ul>
 *
 * <p>Every message states the status, the request URI and (when available) the server-provided
 * error detail.</p>
 */
public final class HttpStatusMapper {

    /** The kind of operation the HTTP response belongs to (drives the 404 mapping). */
    public enum OperationKind {
        CREATE, UPDATE, DELETE, GET, SEARCH
    }

    private HttpStatusMapper() {
    }

    /**
     * Maps the given HTTP status to an ICF exception.
     *
     * @param status the HTTP response status code
     * @param kind   the operation kind (drives the 404 mapping)
     * @param uri    the request URI (for the message)
     * @param uid    the object UID the operation targeted, or {@code null}
     * @param detail the server-provided error detail (e.g. the RFC 7644 {@code detail} field),
     *               or {@code null}/blank when there is none
     * @return the mapped ICF exception
     */
    public static RuntimeException map(int status, OperationKind kind, String uri, String uid, String detail) {
        String base = "HTTP " + status + " at " + uri;
        String tail = (detail == null || detail.isBlank()) ? "" : ": " + detail;
        switch (status) {
            case 401:
                return new InvalidCredentialException("Authentication failed: " + base + tail);
            case 403:
                return new PermissionDeniedException("Permission denied: " + base + tail);
            case 404:
                if (kind == OperationKind.SEARCH) {
                    return new ConfigurationException("Search endpoint returned 404 (check the endpoint path): " + base + tail);
                }
                String subject = (uid == null || uid.isBlank()) ? "Object" : "Object " + uid;
                return new UnknownUidException(subject + " not found: " + base + tail);
            case 409:
                return new AlreadyExistsException("Object already exists: " + base + tail);
            case 429:
                return RetryableException.wrap("Rate limited by the server: " + base + tail, (Throwable) null);
            default:
                break;
        }
        if (status >= 500) {
            return new ConnectionFailedException("Server error: " + base + tail);
        }
        return new ConnectorException("Request was rejected: " + base + tail);
    }
}
