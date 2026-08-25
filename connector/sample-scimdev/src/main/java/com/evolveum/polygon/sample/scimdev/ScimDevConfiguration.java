/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sample.scimdev;

import com.evolveum.polygon.scimrest.config.ScimClientConfiguration;
import com.evolveum.polygon.conndev.groovy.BaseGroovyConnectorConfiguration;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.spi.ConfigurationProperty;

public class ScimDevConfiguration extends BaseGroovyConnectorConfiguration implements ScimClientConfiguration.BearerTokenAuthorization {

    private GuardedString tokenValue;
    private Boolean flattenSingleValueComplexAttributes;

    public void setScimTokenValue(GuardedString tokenValue) {
        this.tokenValue = tokenValue;
    }

    @Override
    @ConfigurationProperty(groupMessageKey = "scim.auth.tokenValue", required = true)
    public GuardedString getScimTokenValue() {
        return tokenValue;
    }

    @Override
    @ConfigurationProperty(groupMessageKey = "scim.flattenSingleValueComplexAttributes", required = false)
    public Boolean getScimFlattenSingleValueComplexAttributes() {
        return flattenSingleValueComplexAttributes == null ? Boolean.FALSE : flattenSingleValueComplexAttributes;
    }

    public void setScimFlattenSingleValueComplexAttributes(Boolean flattenSingleValueComplexAttributes) {
        this.flattenSingleValueComplexAttributes = flattenSingleValueComplexAttributes;
    }

    @Override
    public String getScimBaseUrl() {
        return "https://api.scim.dev/scim/v2";
    }
}
