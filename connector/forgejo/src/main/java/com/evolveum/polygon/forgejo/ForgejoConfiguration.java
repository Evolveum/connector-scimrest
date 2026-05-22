/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.forgejo;

import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.BaseRestGroovyConnectorConfiguration;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.spi.ConfigurationProperty;

public class ForgejoConfiguration extends BaseRestGroovyConnectorConfiguration implements RestClientConfiguration.BearerTokenAuthorization {

    private GuardedString tokenValue;

    @Override
    @ConfigurationProperty(groupMessageKey = "rest.auth.tokenValue", required = true)
    public GuardedString getRestTokenValue() {
        return tokenValue;
    }

    public void setAuthorizationTokenValue(GuardedString tokenValue) {
        this.tokenValue = tokenValue;
    }
}
