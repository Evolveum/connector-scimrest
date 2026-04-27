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
     * OAuth 2.0 Client Credentials authorization configuration.
     *
     * The connector uses the provided credentials to obtain an access token from the authorization server
     * and attaches it as a Bearer token to every outgoing request. Tokens are cached and refreshed
     * automatically when they expire.
     */
    interface OAuth2Authorization extends RestClientConfiguration {

        /**
         * URL of the OAuth 2.0 authorization server token endpoint.
         *
         * @return the token endpoint URL as a String
         */
        String getRestOAuth2TokenUrl();

        /**
         * Client identifier issued by the authorization server.
         *
         * @return the client ID as a String
         */
        String getRestOAuth2ClientId();

        /**
         * Client secret issued by the authorization server.
         *
         * @return the client secret as a {@link GuardedString}
         */
        GuardedString getRestOAuth2ClientSecret();

    }


    static boolean isConfigured(Class<? extends RestClientConfiguration> type, RestClientConfiguration configuration) {
        if (OAuth2Authorization.class.isAssignableFrom(type)) {
            return (configuration instanceof OAuth2Authorization oauth2
                    && oauth2.getRestOAuth2TokenUrl() != null
                    && oauth2.getRestOAuth2ClientId() != null
                    && oauth2.getRestOAuth2ClientSecret() != null);
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
