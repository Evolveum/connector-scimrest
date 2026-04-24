/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.rest;

import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared stateful context for the OAuth2 token lifecycle.
 *
 * <p>A single instance lives in {@link OAuth2TokenManager} for the duration of the connector's
 * lifetime and holds the token state across all lifecycle stages.
 *
 * <h2>Standard keys</h2>
 * The token manager reads and writes the following keys automatically:
 * <ul>
 *   <li>{@link #ACCESS_TOKEN}  — the current Bearer token</li>
 *   <li>{@link #EXPIRES_IN}    — lifetime in seconds as returned by the server</li>
 *   <li>{@link #EXPIRES_AT}    — computed {@link Instant} when the token expires (with buffer)</li>
 *   <li>{@link #REFRESH_TOKEN} — refresh token, if the server returned one</li>
 * </ul>
 */
public class OAuth2Context {

    public static final String ACCESS_TOKEN  = "access_token";
    public static final String EXPIRES_IN    = "expires_in";
    public static final String EXPIRES_AT    = "expires_at";
    public static final String REFRESH_TOKEN = "refresh_token";
    public static final String CLIENT_ID     = "client_id";
    public static final String CLIENT_SECRET = "client_secret";
    public static final String GRANT_TYPE    = "grant_type";
    public static final String SCOPE         = "scope";

    private final Map<String, Object> attributes = new LinkedHashMap<>();
    private RestClientConfiguration.OAuth2Authorization configuration;

    public RestClientConfiguration.OAuth2Authorization getConfiguration() {
        return configuration;
    }

    /**
     * Returns a value previously stored under {@code key}, or {@code null} if absent.
     */
    public Object get(String key) {
        return attributes.get(key);
    }

    /**
     * Stores {@code value} under {@code key}.
     */
    public void set(String key, Object value) {
        attributes.put(key, value);
    }

    /** Groovy {@code []} read operator — equivalent to {@link #get}. */
    public Object getAt(String key) {
        return get(key);
    }

    /** Groovy {@code []} write operator — equivalent to {@link #set}. */
    public void putAt(String key, Object value) {
        set(key, value);
    }

    void setConfiguration(RestClientConfiguration.OAuth2Authorization configuration) {
        this.configuration = configuration;
    }

    public String accessToken() {
        return (String) attributes.get(ACCESS_TOKEN);
    }

    String refreshToken() {
        return (String) attributes.get(REFRESH_TOKEN);
    }

    Long expiresIn() {
        Object val = attributes.get(EXPIRES_IN);
        if (val == null) return null;
        if (val instanceof Long l) return l;
        if (val instanceof Number n) return n.longValue();
        return null;
    }

    Instant expiresAt() {
        Object val = attributes.get(EXPIRES_AT);
        return val instanceof Instant i ? i : null;
    }
}
