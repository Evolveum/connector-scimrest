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
import org.identityconnectors.framework.spi.ConfigurationClass;
import org.identityconnectors.framework.spi.ConfigurationProperty;

@ConfigurationClass(overrideFile = "configurationOverride.properties")
public class ReadOnlyConfiguration extends BaseGroovyConnectorConfiguration implements
        ScimClientConfiguration.BearerToken,
        RestClientConfiguration.BasicAuthorization,
        RestClientConfiguration.ApiKeyAuthorization,
        RestClientConfiguration.TokenAuthorization {


    private String restTokenName;
    private GuardedString restTokenValue;
    private String baseAddress;
    private Boolean trustAllCertificates;
    private GuardedString scimBearerToken;
    private String scimBaseUrl;


    private GuardedString restPassword;
    private String restUsername;
    private GuardedString restApiKey;
    private String restTestEndpoint;

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.auth.tokenName")
    public String getRestTokenName() {
        return restTokenName;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.auth.tokenValue")
    public GuardedString getRestTokenValue() {
        return restTokenValue;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.address.base")
    public String getBaseAddress() {
        return baseAddress;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.address.test")
    public String getRestTestEndpoint() {
        return restTestEndpoint;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "ssl.trustAll")
    public Boolean getTrustAllCertificates() {
        return trustAllCertificates;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.auth.tokenValue")
    public GuardedString getScimBearerToken() {
        return scimBearerToken;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.address.base")
    public String getScimBaseUrl() {
        return scimBaseUrl;
    }



    public void setRestTestEndpoint(String restTestEndpoint) {
        this.restTestEndpoint = restTestEndpoint;
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

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.auth.apiKey")
    public GuardedString getRestApiKey() {
        return this.restApiKey;
    }

    @Override

    @ConfigurationProperty(displayMessageKey = "rest.auth.username")
    public String getRestUsername() {
        return this.restUsername;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.auth.password")
    public GuardedString getRestPassword() {
        return this.restPassword;
    }

    public void setRestPassword(GuardedString restPassword) {
        this.restPassword = restPassword;
    }

    public void setRestUsername(String restUsername) {
        this.restUsername = restUsername;
    }

    public void setRestApiKey(GuardedString restApiKey) {
        this.restApiKey = restApiKey;
    }
}
