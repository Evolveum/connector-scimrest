/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.scim;

import org.identityconnectors.framework.common.exceptions.ConnectorException;

import java.net.URI;

/**
 * Carries the HTTP error status of a failed SCIM request together with the RFC 7644 error
 * document fields ({@code detail}, {@code scimType}) and the request URI.
 *
 * <p>Thrown by {@link ScimHttpErrorFilter} (which runs at the HTTP layer, where the operation
 * kind is not known). Operation handlers catch it and translate it to the concrete ICF type
 * for their operation via {@link ScimExceptionMapper} — e.g. HTTP 404 becomes
 * {@code UnknownUidException} for a retrieve but {@code ConfigurationException} for a search.
 * The server-provided {@code detail} (the single most useful diagnostic a SCIM server
 * produces) is preserved so the eventual error message carries it.</p>
 */
public class ScimHttpErrorException extends ConnectorException {

    private static final long serialVersionUID = 1L;

    private final int status;
    private final String detail;
    private final String scimType;
    private final URI requestUri;

    public ScimHttpErrorException(int status, String detail, String scimType, URI requestUri) {
        super(message(status, detail, requestUri));
        this.status = status;
        this.detail = detail;
        this.scimType = scimType;
        this.requestUri = requestUri;
    }

    private static String message(int status, String detail, URI requestUri) {
        StringBuilder sb = new StringBuilder("SCIM request failed with HTTP ").append(status);
        if (requestUri != null) {
            sb.append(" at ").append(requestUri);
        }
        if (detail != null && !detail.isBlank()) {
            sb.append(": ").append(detail);
        }
        return sb.toString();
    }

    /** The HTTP response status code. */
    public int status() {
        return status;
    }

    /** The RFC 7644 {@code detail} (or raw body text) from the error response, or {@code null}. */
    public String detail() {
        return detail;
    }

    /** The RFC 7644 {@code scimType} from the error response, or {@code null}. */
    public String scimType() {
        return scimType;
    }

    /** The URI of the failed request, or {@code null} when it could not be determined. */
    public URI requestUri() {
        return requestUri;
    }
}
