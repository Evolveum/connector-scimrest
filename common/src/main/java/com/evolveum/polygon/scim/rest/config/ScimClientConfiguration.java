package com.evolveum.polygon.scim.rest.config;

import org.identityconnectors.common.security.GuardedString;

public interface ScimClientConfiguration extends ConfigurationMixin {

    String getScimBaseUrl();

    interface HttpBasic extends ScimClientConfiguration {

        String getScimUsername();

        GuardedString getScimPassword();

    }

    interface BearerToken extends ScimClientConfiguration {

        GuardedString getScimBearerToken();

        default boolean isScimBearerTokenConfigured() {
            return getScimBaseUrl() != null && getScimBearerToken() != null;
        }
    }

    interface OAuthGrant extends ScimClientConfiguration {

        GuardedString getScimOAuthClientId();

        GuardedString getScimOAuthClientSecret();

        String getScimOAuthTokenUrl();

        String getScimOAuthAuthorizationUrl();
    }

}
