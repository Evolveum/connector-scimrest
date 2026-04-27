/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.config;

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.spi.ConfigurationProperty;

public interface RestClientConfiguration extends ConfigurationMixin {

    String getBaseAddress();

    Boolean getTrustAllCertificates();

    @ConfigurationProperty(displayMessageKey = "rest.address.test")
    String getRestTestEndpoint();

    @ConfigurationProperty(displayMessageKey = "rest.timeout.seconds", required = false)
    default Integer getTimeoutSeconds() {
        return 30;
    }

    /**
     *
     * Represents an HTTP Basic Authorization configuration.
     *
     * This configuration extends {@link RestClientConfiguration} and can be used
     * to provide the necessary credentials for performing HTTP Basic Authentication.
     *
     * Should be used only in connectors to systems which does not support Bearer or Token based authorization.
     */
    interface BasicAuthorization extends RestClientConfiguration {

        /**
         * Username required for HTTP Basic Authorization.
         *
         * @return the username as a String
         */
        String getRestUsername();

        /**
         * Password required for HTTP Basic Authorization.
         *
         * @return the password as a {@link GuardedString} object
         */
        GuardedString getRestPassword();

    }

    /**
     * HTTP Bearer Token based configuration
     */
    interface TokenAuthorization extends RestClientConfiguration {

        String getRestTokenName();

        GuardedString getRestTokenValue();

    }

    /**
     * HTTP API Key based authorization configuration.
     *
     * The API key is sent as a custom header or query parameter depending on the connector implementation.
     * Use this for systems that authenticate via a static API key rather than user credentials or tokens.
     */
    interface ApiKeyAuthorization extends RestClientConfiguration {

        /**
         * The API key used for authentication.
         *
         * @return the API key as a {@link GuardedString}
         */
        GuardedString getRestApiKey();
    }

    /**
     * OAuth 2.0 authorization configuration.
     *
     * Supports multiple grant types selected via {@link #getRestOAuth2GrantType()}:
     * <ul>
     *   <li>{@code client_credentials} (default) — authenticates with client ID and secret.</li>
     *   <li>{@code urn:ietf:params:oauth:grant-type:jwt-bearer} (RFC 7523) — authenticates by signing
     *       a JWT assertion with a private key; no client secret is needed.</li>
     * </ul>
     *
     * Tokens are cached and refreshed automatically when they expire.
     */
    interface OAuth2Authorization extends RestClientConfiguration {

        /** URL of the OAuth 2.0 authorization server token endpoint. */
        String getRestOAuth2TokenUrl();

        /**
         * Client identifier issued by the authorization server.
         * Used as {@code client_id} for {@code client_credentials} and as the {@code iss}/{@code sub}
         * claims for {@code jwt-bearer}.
         */
        String getRestOAuth2ClientId();

        /**
         * Client secret. Required for {@code client_credentials} grant type.
         * Not used for {@code jwt-bearer}.
         */
        GuardedString getRestOAuth2ClientSecret();

        /**
         * OAuth 2.0 grant type. Defaults to {@code client_credentials}.
         * Use {@code jwt_bearer} for JWT Bearer (RFC 7523).
         */
        @ConfigurationProperty(displayMessageKey = "rest.oauth2.grantType")
        default String getRestOAuth2GrantType() {
            return OAuth2GrantType.CLIENT_CREDENTIALS.getName();
        }

        /**
         * PEM-encoded PKCS#8 private key used to sign the JWT assertion.
         * Required for {@code jwt-bearer} grant type. Not used for {@code client_credentials}.
         */
        @ConfigurationProperty(displayMessageKey = "rest.oauth2.privateKey")
        GuardedString getRestOAuth2PrivateKey();

    }


    static boolean isConfigured(Class<? extends RestClientConfiguration> type, RestClientConfiguration configuration) {
        if (OAuth2Authorization.class.isAssignableFrom(type)) {
            if (!(configuration instanceof OAuth2Authorization oauth2)) return false;
            if (oauth2.getRestOAuth2TokenUrl() == null || oauth2.getRestOAuth2ClientId() == null) return false;
            if (OAuth2GrantType.JWT_BEARER.equals(oauth2.getRestOAuth2GrantType())) {
                return oauth2.getRestOAuth2PrivateKey() != null;
            }
            return oauth2.getRestOAuth2ClientSecret() != null;
        }
        if (ApiKeyAuthorization.class.isAssignableFrom(type)) {
            return (configuration instanceof ApiKeyAuthorization api && api.getRestApiKey() != null);
        }
        if (TokenAuthorization.class.isAssignableFrom(type)) {
            return (configuration instanceof TokenAuthorization token && token.getRestTokenValue() != null);
        }
        if (BasicAuthorization.class.isAssignableFrom(type)) {
            return (configuration instanceof BasicAuthorization basic && basic.getRestUsername() != null && basic.getRestPassword() != null);
        }
        return false;
    }
}
