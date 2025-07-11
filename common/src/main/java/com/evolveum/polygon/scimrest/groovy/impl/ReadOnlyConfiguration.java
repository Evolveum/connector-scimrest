/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */

package com.evolveum.polygon.scimrest.groovy.impl;

import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.config.ScimClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.BaseGroovyConnectorConfiguration;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.spi.ConfigurationProperty;

public class ReadOnlyConfiguration extends BaseGroovyConnectorConfiguration implements ScimClientConfiguration.BearerToken, RestClientConfiguration.TokenAuthorization {


    private String restTokenName;
    private GuardedString restTokenValue;
    private String baseAddress;
    private Boolean trustAllCertificates;
    private GuardedString scimBearerToken;
    private String scimBaseUrl;

    @Override
    @ConfigurationProperty(groupMessageKey = "rest.auth.tokenName", required = true)
    public String getRestTokenName() {
        return restTokenName;
    }

    @Override
    @ConfigurationProperty(groupMessageKey = "rest.auth.tokenValue", required = true)
    public GuardedString getRestTokenValue() {
        return restTokenValue;
    }

    @Override
    @ConfigurationProperty(groupMessageKey = "rest.address.base", required = true)
    public String getBaseAddress() {
        return baseAddress;
    }


    @Override
    @ConfigurationProperty(groupMessageKey = "ssl.trustAll", required = true)
    public Boolean getTrustAllCertificates() {
        return trustAllCertificates;
    }

    @Override
    @ConfigurationProperty(groupMessageKey = "scim.auth.tokenValue", required = true)
    public GuardedString getScimBearerToken() {
        return scimBearerToken;
    }

    @Override
    @ConfigurationProperty(groupMessageKey = "scim.address.base", required = true)
    public String getScimBaseUrl() {
        return scimBaseUrl;
    }

    public void setRestTokenName(String restTokenName) {
        this.restTokenName = restTokenName;
    }

    public void setRestTokenValue(GuardedString restTokenValue) {
        this.restTokenValue = restTokenValue;
    }

    public void setBaseAddress(String baseAddress) {
        this.baseAddress = baseAddress;
    }

    public void setTrustAllCertificates(Boolean trustAllCertificates) {
        this.trustAllCertificates = trustAllCertificates;
    }

    public void setScimBearerToken(GuardedString scimBearerToken) {
        this.scimBearerToken = scimBearerToken;
    }

    public void setScimBaseUrl(String scimBaseUrl) {
        this.scimBaseUrl = scimBaseUrl;
    }
}
