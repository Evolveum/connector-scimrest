/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.config;

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
