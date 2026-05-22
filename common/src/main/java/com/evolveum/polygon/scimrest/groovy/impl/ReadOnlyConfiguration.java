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
        ScimClientConfiguration.BearerTokenAuthorization,
        ScimClientConfiguration.JwtBearerAuthorization,
        RestClientConfiguration.BasicAuthorization,
        RestClientConfiguration.ApiKeyAuthorization,
        RestClientConfiguration.BearerTokenAuthorization,
        RestClientConfiguration.JwtBearerAuthorization,
        RestClientConfiguration.OAuth2ClientCredentialsAuthorization,
        RestClientConfiguration.OAuth2JwtBearerAuthorization {

    // --- Common ---
    private String baseAddress;
    private Boolean trustAllCertificates = true;
    private String restTestEndpoint;

    // --- REST BasicAuthorization ---
    private String restUsername;
    private GuardedString restPassword;

    // --- REST BearerTokenAuthorization ---
    private GuardedString restTokenValue;

    // --- REST JwtBearerAuthorization ---
    private String restJwtTokenName;
    private String restJwtAlgorithm;
    private GuardedString restJwtSecret;
    private Boolean restJwtSecretBase64Encoded;
    private String restJwtPayload;
    private String restJwtLocalization;

    // --- REST ApiKeyAuthorization ---
    private GuardedString restApiKey;
    private String restApiKeyName;
    private String restApiKeyLocation;

    // --- REST OAuth2 (shared fields) ---
    private String restOAuth2TokenUrl;
    private String restOAuth2ClientId;

    // --- REST OAuth2ClientCredentialsAuthorization ---
    private GuardedString restOAuth2ClientSecret;

    // --- REST OAuth2JwtBearerAuthorization ---
    private GuardedString restOAuth2PrivateKey;

    // --- SCIM BearerTokenAuthorization ---
    private GuardedString scimTokenValue;

    // --- SCIM JwtBearerAuthorization ---
    private String scimJwtTokenName;
    private String scimJwtAlgorithm;
    private GuardedString scimJwtSecret;
    private Boolean scimJwtSecretBase64Encoded;
    private String scimJwtPayload;
    private String scimJwtLocalization;

    private String scimBaseUrl;

    // =========================================================================
    // Common
    // =========================================================================

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.address.base")
    public String getBaseAddress() {
        return baseAddress;
    }

    public void setBaseAddress(String baseAddress) {
        this.baseAddress = baseAddress;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "ssl.trustAll")
    public Boolean getTrustAllCertificates() {
        return trustAllCertificates;
    }

    public void setTrustAllCertificates(Boolean trustAllCertificates) {
        this.trustAllCertificates = trustAllCertificates;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.address.test")
    public String getRestTestEndpoint() {
        return restTestEndpoint;
    }

    public void setRestTestEndpoint(String restTestEndpoint) {
        this.restTestEndpoint = restTestEndpoint;
    }

    // =========================================================================
    // REST BasicAuthorization
    // =========================================================================

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.basic.username")
    public String getRestUsername() {
        return restUsername;
    }

    public void setRestUsername(String restUsername) {
        this.restUsername = restUsername;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.basic.password")
    public GuardedString getRestPassword() {
        return restPassword;
    }

    public void setRestPassword(GuardedString restPassword) {
        this.restPassword = restPassword;
    }

    // =========================================================================
    // REST BearerTokenAuthorization
    // =========================================================================

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.token.value", order = 10)
    public GuardedString getRestTokenValue() {
        return restTokenValue;
    }

    public void setRestTokenValue(GuardedString restTokenValue) {
        this.restTokenValue = restTokenValue;
    }

    // =========================================================================
    // REST JwtBearerAuthorization
    // =========================================================================

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.token.name", order = 10)
    public String getRestJwtTokenName() {
        return restJwtTokenName;
    }

    public void setRestJwtTokenName(String restJwtTokenName) {
        this.restJwtTokenName = restJwtTokenName;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.token.jwt.algorithm", order = 20)
    public String getRestJwtAlgorithm() {
        return restJwtAlgorithm;
    }

    public void setRestJwtAlgorithm(String restJwtAlgorithm) {
        this.restJwtAlgorithm = restJwtAlgorithm;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.token.jwt.secret", order = 30)
    public GuardedString getRestJwtSecret() {
        return restJwtSecret;
    }

    public void setRestJwtSecret(GuardedString restJwtSecret) {
        this.restJwtSecret = restJwtSecret;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.token.jwt.secretBase64", order = 40)
    public Boolean getRestJwtSecretBase64Encoded() {
        return restJwtSecretBase64Encoded;
    }

    public void setRestJwtSecretBase64Encoded(Boolean restJwtSecretBase64Encoded) {
        this.restJwtSecretBase64Encoded = restJwtSecretBase64Encoded;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.token.jwt.payload", order = 50)
    public String getRestJwtPayload() {
        return restJwtPayload;
    }

    public void setRestJwtPayload(String restJwtPayload) {
        this.restJwtPayload = restJwtPayload;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.token.jwt.localization", order = 60)
    public String getRestJwtLocalization() {
        return restJwtLocalization;
    }

    public void setRestJwtLocalization(String restJwtLocalization) {
        this.restJwtLocalization = restJwtLocalization;
    }

    // =========================================================================
    // REST ApiKeyAuthorization
    // =========================================================================

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.apikey.value")
    public GuardedString getRestApiKey() {
        return restApiKey;
    }

    public void setRestApiKey(GuardedString restApiKey) {
        this.restApiKey = restApiKey;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.apikey.name")
    public String getRestApiKeyName() {
        return restApiKeyName;
    }

    public void setRestApiKeyName(String restApiKeyName) {
        this.restApiKeyName = restApiKeyName;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.apikey.location")
    public String getRestApiKeyLocation() {
        return restApiKeyLocation;
    }

    public void setRestApiKeyLocation(String restApiKeyLocation) {
        this.restApiKeyLocation = restApiKeyLocation;
    }

    // =========================================================================
    // REST OAuth2 — shared
    // =========================================================================

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.oauth2.tokenUrl")
    public String getRestOAuth2TokenUrl() {
        return restOAuth2TokenUrl;
    }

    public void setRestOAuth2TokenUrl(String restOAuth2TokenUrl) {
        this.restOAuth2TokenUrl = restOAuth2TokenUrl;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.oauth2.clientId")
    public String getRestOAuth2ClientId() {
        return restOAuth2ClientId;
    }

    public void setRestOAuth2ClientId(String restOAuth2ClientId) {
        this.restOAuth2ClientId = restOAuth2ClientId;
    }

    // =========================================================================
    // REST OAuth2ClientCredentialsAuthorization
    // =========================================================================

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.oauth2.clientSecret")
    public GuardedString getRestOAuth2ClientSecret() {
        return restOAuth2ClientSecret;
    }

    public void setRestOAuth2ClientSecret(GuardedString restOAuth2ClientSecret) {
        this.restOAuth2ClientSecret = restOAuth2ClientSecret;
    }

    // =========================================================================
    // REST OAuth2JwtBearerAuthorization
    // =========================================================================

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.oauth2.privateKey")
    public GuardedString getRestOAuth2PrivateKey() {
        return restOAuth2PrivateKey;
    }

    public void setRestOAuth2PrivateKey(GuardedString restOAuth2PrivateKey) {
        this.restOAuth2PrivateKey = restOAuth2PrivateKey;
    }

    // =========================================================================
    // SCIM BearerTokenAuthorization
    // =========================================================================

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.token.value", order = 10)
    public GuardedString getScimTokenValue() {
        return scimTokenValue;
    }

    public void setScimTokenValue(GuardedString scimTokenValue) {
        this.scimTokenValue = scimTokenValue;
    }

    // =========================================================================
    // SCIM JwtBearerAuthorization
    // =========================================================================

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.token.name", order = 10)
    public String getScimJwtTokenName() {
        return scimJwtTokenName;
    }

    public void setScimJwtTokenName(String scimJwtTokenName) {
        this.scimJwtTokenName = scimJwtTokenName;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.token.jwt.algorithm", order = 20)
    public String getScimJwtAlgorithm() {
        return scimJwtAlgorithm;
    }

    public void setScimJwtAlgorithm(String scimJwtAlgorithm) {
        this.scimJwtAlgorithm = scimJwtAlgorithm;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.token.jwt.secret", order = 30)
    public GuardedString getScimJwtSecret() {
        return scimJwtSecret;
    }

    public void setScimJwtSecret(GuardedString scimJwtSecret) {
        this.scimJwtSecret = scimJwtSecret;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.token.jwt.secretBase64", order = 40)
    public Boolean getScimJwtSecretBase64Encoded() {
        return scimJwtSecretBase64Encoded;
    }

    public void setScimJwtSecretBase64Encoded(Boolean scimJwtSecretBase64Encoded) {
        this.scimJwtSecretBase64Encoded = scimJwtSecretBase64Encoded;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.token.jwt.payload", order = 50)
    public String getScimJwtPayload() {
        return scimJwtPayload;
    }

    public void setScimJwtPayload(String scimJwtPayload) {
        this.scimJwtPayload = scimJwtPayload;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.token.jwt.localization", order = 60)
    public String getScimJwtLocalization() {
        return scimJwtLocalization;
    }

    public void setScimJwtLocalization(String scimJwtLocalization) {
        this.scimJwtLocalization = scimJwtLocalization;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.address.base")
    public String getScimBaseUrl() {
        return scimBaseUrl;
    }

    public void setScimBaseUrl(String scimBaseUrl) {
        this.scimBaseUrl = scimBaseUrl;
    }

}
