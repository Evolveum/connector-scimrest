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

    interface ApiKeyAuthorization extends RestClientConfiguration {

        GuardedString getRestApiKey();
    }

    static boolean isConfigured(Class<? extends RestClientConfiguration> type, RestClientConfiguration configuration) {
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
