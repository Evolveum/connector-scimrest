/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.scim;

import com.evolveum.polygon.conndev.logging.ConnDevLog;
import com.evolveum.polygon.scimrest.logging.ProtocolTrace;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.client.ClientResponseContext;
import jakarta.ws.rs.client.ClientResponseFilter;

/**
 * Attaches HTTP protocol events of the SCIM stack (UnboundID SDK over Jersey) to the operation
 * entry currently active on this thread.
 *
 * <p>Only plain-string entities are logged: streaming entities are left untouched so that the
 * downstream SCIM client can still consume them.
 */
public final class ScimProtocolLogFilter implements ClientRequestFilter, ClientResponseFilter {

    private static final ConnDevLog LOG = ConnDevLog.of(ScimContext.class);

    @Override
    public void filter(ClientRequestContext requestContext) {
        ProtocolTrace.request(LOG, requestContext.getMethod(),
                requestContext.getUri().toString(), stringEntity(requestContext.getEntity()));
    }

    @Override
    public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) {
        ProtocolTrace.response(LOG, responseContext.getStatus(),
                requestContext.getUri().toString(), null);
    }

    private static Object stringEntity(Object entity) {
        return entity instanceof String ? entity : null;
    }
}
