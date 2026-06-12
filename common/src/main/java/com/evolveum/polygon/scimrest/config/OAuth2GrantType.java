/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.config;

/**
 * Supported OAuth 2.0 grant types.
 *
 * <p>Each constant carries its canonical grant type string via {@link #getName()}.
 * Use {@link #parse(String)} to resolve a configured string to an enum value.
 */
public enum OAuth2GrantType {

    CLIENT_CREDENTIALS("client_credentials"),

    /** Also accepted as the short alias {@code jwt_bearer}. */
    JWT_BEARER("urn:ietf:params:oauth:grant-type:jwt-bearer"),

    /** @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-4.3">RFC 6749 §4.3</a> */
    PASSWORD("password"),

    /** @see <a href="https://www.rfc-editor.org/rfc/rfc7522">RFC 7522</a> */
    SAML_BEARER("urn:ietf:params:oauth:grant-type:saml2-bearer");

    private final String name;

    OAuth2GrantType(String name) {
        this.name = name;
    }

    /** Returns the canonical OAuth 2.0 grant type string sent to the token endpoint. */
    public String getName() {
        return name;
    }

    /**
     * Returns {@code true} if {@code value} matches this grant type.
     * {@link #JWT_BEARER} additionally accepts the short alias {@code jwt_bearer}.
     */
    public boolean equals(String value) {
        if (this.name.equals(value)) return true;
        return this == JWT_BEARER && "jwt_bearer".equals(value);
    }

    /**
     * Resolves a grant type string to the corresponding enum constant.
     *
     * @throws IllegalArgumentException if {@code value} is {@code null} or not recognised
     */
    public static OAuth2GrantType parse(String value) {
        if (value == null) throw new IllegalArgumentException("OAuth2 grant type must be configured");
        for (OAuth2GrantType type : values()) {
            if (type.equals(value)) return type;
        }
        throw new IllegalArgumentException("Unsupported OAuth2 grant type: " + value);
    }
}
