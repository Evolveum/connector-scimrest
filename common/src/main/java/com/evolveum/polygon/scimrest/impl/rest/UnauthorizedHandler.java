/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.rest;

/**
 * Implemented by authorization customizers that can react to a 401 Unauthorized response
 * by refreshing their internal state and signalling whether the caller should retry.
 *
 * <p>The canonical example is an OAuth2 token manager that invalidates a stale cached token
 * so the next {@code customize} invocation fetches a fresh one from the authorization server.
 */
public interface UnauthorizedHandler {

    /**
     * Called when the server returns a 401 response. If the handler invalidated stale state
     * (e.g. cleared a cached OAuth2 token) it returns {@code true}, indicating that the caller
     * should re-apply authorization and retry the request exactly once.
     */
    boolean onUnauthorized();
}
