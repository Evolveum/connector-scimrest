package com.evolveum.polygon.scim.rest.config;

import org.identityconnectors.common.security.GuardedString;

public interface HttpBasicAuthorizationConfiguration {

    String getUsername();

    GuardedString getPassword();

}
