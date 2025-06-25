/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.forgejo;

import com.evolveum.polygon.scim.rest.config.RestClientConfiguration;
import com.evolveum.polygon.scim.rest.groovy.BaseRestGroovyConnectorConfiguration;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.spi.ConfigurationProperty;

public class ForgejoConfiguration extends BaseRestGroovyConnectorConfiguration implements RestClientConfiguration.TokenAuthorization {


    private String tokenName;
    private GuardedString tokenValue;

    @Override
    @ConfigurationProperty(groupMessageKey = "rest.auth.tokenName", required = false)
    public String getRestTokenName() {
        return tokenName;
    }

    @Override
    @ConfigurationProperty(groupMessageKey = "rest.auth.tokenValue", required = true)
    public GuardedString getRestTokenValue() {
        return tokenValue;
    }

    public void setAuthorizationTokenName(String tokenName) {
        this.tokenName = tokenName;
    }

    public void setAuthorizationTokenValue(GuardedString tokenValue) {
        this.tokenValue = tokenValue;
    }
}
