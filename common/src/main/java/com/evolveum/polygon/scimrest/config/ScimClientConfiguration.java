/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.config;

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.spi.ConfigurationProperty;

public interface ScimClientConfiguration extends ConfigurationMixin {

    String getScimBaseUrl();

    interface HttpBasic extends ScimClientConfiguration {

        String getScimUsername();

        GuardedString getScimPassword();

    }

    /**
     * Simple Bearer token authorization — sends the token as {@code Authorization: Bearer <value>}.
     */
    interface BearerTokenAuthorization extends ScimClientConfiguration {
        GuardedString getScimTokenValue();
    }

    /**
     * JWT Bearer token authorization — generates a signed JWT assertion and sends it as a token.
     */
    interface JwtBearerAuthorization extends ScimClientConfiguration {

        /** Token prefix placed before the JWT value (e.g. {@code Bearer}). */
        String getScimJwtTokenName();

        /** JWT signing algorithm. */
        @ConfigurationProperty(allowedValues = {"HS256", "HS384", "HS512", "RS256", "RS384", "RS512", "PS256", "PS384", "PS512", "ES256", "ES384", "ES512"})
        String getScimJwtAlgorithm();

        /** Secret or private key used to sign the JWT. */
        GuardedString getScimJwtSecret();

        /** When true, {@link #getScimJwtSecret()} is Base64-encoded. */
        Boolean getScimJwtSecretBase64Encoded();

        /** JSON string with custom JWT claims to merge into the payload. */
        String getScimJwtPayload();

        /** Where in the request the token is placed: {@code header} (default) or {@code query}. */
        String getScimJwtLocalization();
    }

    interface ApiKeyAuthorization extends ScimClientConfiguration {

        GuardedString getScimApiKey();

        /** Name of the header or query parameter that carries the key (e.g. {@code X-API-Key}, {@code api_key}). */
        @ConfigurationProperty(displayMessageKey = "scim.apikey.name")
        String getScimApiKeyName();

        /** Where to place the key: {@code header} or {@code query}. */
        @ConfigurationProperty(displayMessageKey = "scim.apikey.location")
        String getScimApiKeyLocation();

    }

    /**
     * OAuth 2.0 Client Credentials grant authorization.
     * Authenticates with client ID and secret to obtain an access token.
     */
    interface OAuth2ClientCredentialsAuthorization extends ScimClientConfiguration {
        String getScimOAuth2TokenUrl();
        String getScimOAuth2ClientId();
        GuardedString getScimOAuth2ClientSecret();
    }

    /**
     * OAuth 2.0 JWT Bearer grant authorization (RFC 7523).
     * Authenticates by signing a JWT assertion with a private key; no client secret needed.
     */
    interface OAuth2JwtBearerAuthorization extends ScimClientConfiguration {
        String getScimOAuth2TokenUrl();
        String getScimOAuth2ClientId();
        GuardedString getScimOAuth2PrivateKey();
    }

    static boolean isConfigured(Class<? extends ScimClientConfiguration> type, ScimClientConfiguration configuration) {
        if (OAuth2JwtBearerAuthorization.class.isAssignableFrom(type)) {
            return configuration instanceof OAuth2JwtBearerAuthorization o
                    && o.getScimOAuth2TokenUrl() != null && o.getScimOAuth2ClientId() != null
                    && o.getScimOAuth2PrivateKey() != null;
        }
        if (OAuth2ClientCredentialsAuthorization.class.isAssignableFrom(type)) {
            return configuration instanceof OAuth2ClientCredentialsAuthorization o
                    && o.getScimOAuth2TokenUrl() != null && o.getScimOAuth2ClientId() != null
                    && o.getScimOAuth2ClientSecret() != null;
        }
        if (ApiKeyAuthorization.class.isAssignableFrom(type)) {
            return configuration instanceof ApiKeyAuthorization api
                    && api.getScimApiKey() != null && api.getScimApiKeyName() != null;
        }
        if (JwtBearerAuthorization.class.isAssignableFrom(type)) {
            return configuration instanceof JwtBearerAuthorization jwt
                    && jwt.getScimJwtAlgorithm() != null && jwt.getScimJwtSecret() != null;
        }
        if (BearerTokenAuthorization.class.isAssignableFrom(type)) {
            return configuration instanceof BearerTokenAuthorization token
                    && token.getScimTokenValue() != null;
        }
        if (HttpBasic.class.isAssignableFrom(type)) {
            return configuration instanceof HttpBasic basic
                    && basic.getScimUsername() != null && basic.getScimPassword() != null;
        }
        return false;
    }

}
