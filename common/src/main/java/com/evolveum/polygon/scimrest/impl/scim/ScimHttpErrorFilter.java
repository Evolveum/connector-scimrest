/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.scim;

import com.evolveum.polygon.scimrest.impl.rest.ErrorDetail;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientResponseContext;
import jakarta.ws.rs.client.ClientResponseFilter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Turns SCIM HTTP error responses (status &gt;= 400) into {@link ScimHttpErrorException}s that
 * carry the status, the request URI and the RFC 7644 error document's {@code detail} (the
 * single most useful diagnostic a SCIM server produces) instead of a bare reason phrase.
 *
 * <p>The filter runs at the HTTP layer, where the operation kind is not known, so it always
 * throws {@link ScimHttpErrorException}; the operation handlers translate it to the concrete
 * ICF type for their operation (e.g. 404 to {@code UnknownUidException} on a retrieve) via
 * {@link ScimExceptionMapper}. The error body is bounded when read, so a verbose error page
 * cannot blow up the message.</p>
 */
public class ScimHttpErrorFilter implements ClientResponseFilter {

    private static final int MAX_ERROR_BODY_BYTES = 8 * 1024;

    @Override
    public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) throws IOException {
        int status = responseContext.getStatus();
        if (status < 400) {
            return;
        }
        String detail = null;
        InputStream entityStream = responseContext.getEntityStream();
        if (entityStream != null) {
            detail = ErrorDetail.extract(readBounded(entityStream, MAX_ERROR_BODY_BYTES));
        }
        throw new ScimHttpErrorException(status, detail, null, requestContext.getUri());
    }

    /** Reads at most {@code maxBytes} from the stream, leaving the rest unread. */
    private static byte[] readBounded(InputStream in, int maxBytes) throws IOException {
        byte[] buffer = new byte[Math.min(maxBytes, 4096)];
        var result = new ByteArrayOutputStream();
        int remaining = maxBytes;
        while (remaining > 0) {
            int read = in.read(buffer, 0, Math.min(buffer.length, remaining));
            if (read < 0) {
                break;
            }
            result.write(buffer, 0, read);
            remaining -= read;
        }
        return result.toByteArray();
    }
}
