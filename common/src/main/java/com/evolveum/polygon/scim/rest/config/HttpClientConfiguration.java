package com.evolveum.polygon.scim.rest.config;

import org.identityconnectors.common.security.GuardedString;

public interface HttpClientConfiguration extends ConfigurationMixin {

    String getBaseAddress();

    Boolean getTrustAllCertificates();

    /**
     * Returns extended configuration such as {@link BasicAuthorization} or {@link TokenAuthorization}.
     *
     * Default implementation returns the current instance casted to extended configuration
     * otherwise throws an {@link IllegalStateException}.
     *
     * @param <E> the type of the extended configuration
     * @param extendedConfiguration the class of the extended configuration type to cast to
     * @return an instance of the specified extended configuration type
     * @throws IllegalStateException if the current instance is not of the specified type
     */
    default  <E extends HttpClientConfiguration> E require(Class<E> extendedConfiguration) {
        if (extendedConfiguration.isInstance(this)) {
            return extendedConfiguration.cast(this);
        }
        throw new IllegalStateException("Configuration of type " + extendedConfiguration.getSimpleName() + " required");
    }

    /**
     *
     * Represents an HTTP Basic Authorization configuration.
     * This configuration extends {@link HttpClientConfiguration} and can be used
     * to provide the necessary credentials for performing HTTP Basic Authentication.
     *
     * Should be used only in connectors to systems which does not support Bearer or Token based authorization.
     */
    interface BasicAuthorization extends HttpClientConfiguration {

        /**
         * Username required for HTTP Basic Authorization.
         *
         * @return the username as a String
         */
        String getUsername();

        /**
         * Password required for HTTP Basic Authorization.
         *
         * @return the password as a {@link GuardedString} object
         */
        GuardedString getPassword();

    }

    /**
     * HTTP Bearer Token based configuration
     */
    interface TokenAuthorization extends HttpClientConfiguration {

        String getAuthorizationTokenName();

        GuardedString getAuthorizationTokenValue();

    }
}
