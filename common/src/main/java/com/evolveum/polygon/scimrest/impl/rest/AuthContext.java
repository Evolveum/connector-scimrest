/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.rest;

import com.evolveum.polygon.common.GuardedStringAccessor;
import org.identityconnectors.common.security.GuardedString;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared stateful context for auth credential lifecycles.
 *
 * <p>Groovy hooks access {@link #getConfiguration()} and {@link #decrypt(GuardedString)}
 * via this object used as the closure delegate.
 */
public class AuthContext<C> {

    private final Map<String, Object> attributes = new LinkedHashMap<>();
    private C configuration;

    public C getConfiguration()         { return configuration; }
    void setConfiguration(C config)     { this.configuration = config; }

    /** Groovy DSL: {@code authContext.decrypt(x)} — {@code authContext} resolves to {@code this}. */
    public AuthContext<C> getAuthContext() { return this; }

    public String decrypt(GuardedString gs) {
        if (gs == null) return null;
        var accessor = new GuardedStringAccessor();
        gs.access(accessor);
        return accessor.getClearString();
    }

    public Object get(String key)               { return attributes.get(key); }
    public void   set(String key, Object value) { attributes.put(key, value); }

    /** Groovy {@code []} read operator. */
    public Object getAt(String key)             { return get(key); }

    /** Groovy {@code []} write operator. */
    public void   putAt(String key, Object value) { set(key, value); }
}
