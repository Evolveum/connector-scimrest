package com.evolveum.polygon.scim.rest.config;

import org.identityconnectors.common.security.GuardedString;

public interface HttpClientConfiguration extends ConfigurationMixin {

    String getBaseAddress();

    Boolean getTrustAllCertificates();


    /**
     * HTTP Basic Authorization based configuration
     *
     */
    interface BasicAuthorization extends HttpClientConfiguration {

        String getUsername();

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
