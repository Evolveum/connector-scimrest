package com.evolveum.polygon.scim.rest.config;

import org.identityconnectors.common.security.GuardedString;

public interface HttpClientConfiguration {

    String getBaseAddress();

    boolean getTrustAllCertificates();


    interface BasicAuthorization {

        String getUsername();

        GuardedString getPassword();

    }

    interface TokenAuthorization {

        String getAuthorizationTokenName();

        GuardedString getAuthorizationTokenValue();

    }
}
