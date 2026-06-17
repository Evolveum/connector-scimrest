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
        ScimClientConfiguration.BasicAuthorization,
        ScimClientConfiguration.ApiKeyAuthorization,
        ScimClientConfiguration.BearerTokenAuthorization,
        ScimClientConfiguration.JwtBearerAuthorization,
        ScimClientConfiguration.DigestAuthorization,
        ScimClientConfiguration.OAuth2ClientCredentialsAuthorization,
        ScimClientConfiguration.OAuth2PasswordAuthorization,
        ScimClientConfiguration.OAuth2JwtBearerAuthorization,
        ScimClientConfiguration.OAuth2SamlAuthorization,
        ScimClientConfiguration.HawkAuthorization,
        ScimClientConfiguration.AwsSignatureAuthorization,
        ScimClientConfiguration.NtlmAuthorization,
        RestClientConfiguration.BasicAuthorization,
        RestClientConfiguration.ApiKeyAuthorization,
        RestClientConfiguration.BearerTokenAuthorization,
        RestClientConfiguration.JwtBearerAuthorization,
        RestClientConfiguration.DigestAuthorization,
        RestClientConfiguration.OAuth2ClientCredentialsAuthorization,
        RestClientConfiguration.OAuth2PasswordAuthorization,
        RestClientConfiguration.OAuth2JwtBearerAuthorization,
        RestClientConfiguration.OAuth2SamlAuthorization,
        RestClientConfiguration.HawkAuthorization,
        RestClientConfiguration.AwsSignatureAuthorization,
        RestClientConfiguration.NtlmAuthorization {

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
    private String restJwtLocation;

    // --- REST ApiKeyAuthorization ---
    private GuardedString restApiKey;
    private String restApiKeyName;
    private String restApiKeyLocation;

    // --- REST DigestAuthorization ---
    private String restDigestUsername;
    private GuardedString restDigestPassword;
    private Boolean restDigestAutoChallenge;
    private Integer restDigestMaxRetries;
    private Boolean restDigestPreemptiveAuth;
    private String restDigestAlgorithmPreference;
    private Boolean restDigestStateCacheEnabled;

    // --- REST OAuth2 shared ---
    private String restOAuth2TokenUrl;
    private String restOAuth2ClientId;
    private String restOAuth2Scope;
    private String restOAuth2ClientAuthenticationScheme;

    // --- REST OAuth2ClientCredentialsAuthorization ---
    private GuardedString restOAuth2ClientSecret;

    // --- REST OAuth2PasswordAuthorization ---
    private String restOAuth2Username;
    private GuardedString restOAuth2Password;

    // --- REST OAuth2JwtBearerAuthorization / OAuth2SamlAuthorization ---
    private GuardedString restOAuth2PrivateKey;
    private String restOAuth2Issuer;

    // --- REST OAuth2JwtBearerAuthorization ---
    private String restOAuth2KeyId;
    private String restOAuth2Algorithm;
    private String restOAuth2Subject;

    // --- REST HawkAuthorization ---
    private String restHawkId;
    private GuardedString restHawkKey;
    private String restHawkAlgorithm;
    private Boolean restHawkIncludePayloadHash;
    private Integer restHawkOffset;
    private String restHawkExt;

    // --- REST AwsSignatureAuthorization ---
    private String restAwsAccessKey;
    private GuardedString restAwsSecretKey;
    private GuardedString restAwsSessionToken;
    private String restAwsRegion;
    private String restAwsService;

    // --- REST NtlmAuthorization ---
    private String restNtlmUsername;
    private GuardedString restNtlmPassword;
    private String restNtlmDomain;
    private String restNtlmWorkstation;
    private String restNtlmVersion;

    // --- SCIM BasicAuthorization ---
    private String scimUsername;
    private GuardedString scimPassword;

    // --- SCIM BearerTokenAuthorization ---
    private GuardedString scimTokenValue;

    // --- SCIM JwtBearerAuthorization ---
    private String scimJwtTokenName;
    private String scimJwtAlgorithm;
    private GuardedString scimJwtSecret;
    private Boolean scimJwtSecretBase64Encoded;
    private String scimJwtPayload;
    private String scimJwtLocation;

    // --- SCIM ApiKeyAuthorization ---
    private GuardedString scimApiKey;
    private String scimApiKeyName;
    private String scimApiKeyLocation;

    // --- SCIM DigestAuthorization ---
    private String scimDigestUsername;
    private GuardedString scimDigestPassword;
    private Boolean scimDigestAutoChallenge;
    private Integer scimDigestMaxRetries;
    private Boolean scimDigestPreemptiveAuth;
    private String scimDigestAlgorithmPreference;
    private Boolean scimDigestStateCacheEnabled;

    // --- SCIM OAuth2 shared ---
    private String scimOAuth2TokenUrl;
    private String scimOAuth2ClientId;
    private String scimOAuth2Scope;
    private String scimOAuth2ClientAuthenticationScheme;

    // --- SCIM OAuth2ClientCredentialsAuthorization ---
    private GuardedString scimOAuth2ClientSecret;

    // --- SCIM OAuth2PasswordAuthorization ---
    private String scimOAuth2Username;
    private GuardedString scimOAuth2Password;

    // --- SCIM OAuth2JwtBearerAuthorization / OAuth2SamlAuthorization ---
    private GuardedString scimOAuth2PrivateKey;
    private String scimOAuth2Issuer;

    // --- SCIM OAuth2JwtBearerAuthorization ---
    private String scimOAuth2KeyId;
    private String scimOAuth2Algorithm;
    private String scimOAuth2Subject;

    // --- SCIM HawkAuthorization ---
    private String scimHawkId;
    private GuardedString scimHawkKey;
    private String scimHawkAlgorithm;
    private Boolean scimHawkIncludePayloadHash;
    private Integer scimHawkOffset;
    private String scimHawkExt;

    // --- SCIM AwsSignatureAuthorization ---
    private String scimAwsAccessKey;
    private GuardedString scimAwsSecretKey;
    private GuardedString scimAwsSessionToken;
    private String scimAwsRegion;
    private String scimAwsService;

    // --- SCIM NtlmAuthorization ---
    private String scimNtlmUsername;
    private GuardedString scimNtlmPassword;
    private String scimNtlmDomain;
    private String scimNtlmWorkstation;
    private String scimNtlmVersion;

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

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.timeout.seconds", required = false)
    public Integer getTimeoutSeconds() {
        return 30;
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
    @ConfigurationProperty(displayMessageKey = "rest.token.jwt.algorithm", order = 20, allowedValues = {"HS256", "HS384", "HS512", "RS256", "RS384", "RS512", "PS256", "PS384", "PS512", "ES256", "ES384", "ES512"})
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
    @ConfigurationProperty(displayMessageKey = "rest.token.jwt.location", order = 60, allowedValues = {"header", "query"})
    public String getRestJwtLocation() {
        return restJwtLocation;
    }

    public void setRestJwtLocation(String restJwtLocation) {
        this.restJwtLocation = restJwtLocation;
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
    @ConfigurationProperty(displayMessageKey = "rest.apikey.location", allowedValues = {"header", "query"})
    public String getRestApiKeyLocation() {
        return restApiKeyLocation;
    }

    public void setRestApiKeyLocation(String restApiKeyLocation) {
        this.restApiKeyLocation = restApiKeyLocation;
    }

    // =========================================================================
    // REST DigestAuthorization
    // =========================================================================

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.digest.username")
    public String getRestDigestUsername() {
        return restDigestUsername;
    }

    public void setRestDigestUsername(String restDigestUsername) {
        this.restDigestUsername = restDigestUsername;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.digest.password")
    public GuardedString getRestDigestPassword() {
        return restDigestPassword;
    }

    public void setRestDigestPassword(GuardedString restDigestPassword) {
        this.restDigestPassword = restDigestPassword;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.digest.autoChallenge")
    public Boolean getRestDigestAutoChallenge() {
        return restDigestAutoChallenge;
    }

    public void setRestDigestAutoChallenge(Boolean restDigestAutoChallenge) {
        this.restDigestAutoChallenge = restDigestAutoChallenge;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.digest.maxRetries")
    public Integer getRestDigestMaxRetries() {
        return restDigestMaxRetries;
    }

    public void setRestDigestMaxRetries(Integer restDigestMaxRetries) {
        this.restDigestMaxRetries = restDigestMaxRetries;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.digest.preemptiveAuth")
    public Boolean getRestDigestPreemptiveAuth() {
        return restDigestPreemptiveAuth;
    }

    public void setRestDigestPreemptiveAuth(Boolean restDigestPreemptiveAuth) {
        this.restDigestPreemptiveAuth = restDigestPreemptiveAuth;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.digest.algorithmPreference", allowedValues = {"MD5", "MD5-sess", "SHA-256", "SHA-256-sess", "SHA-512-256", "SHA-512-256-sess"})
    public String getRestDigestAlgorithmPreference() {
        return restDigestAlgorithmPreference;
    }

    public void setRestDigestAlgorithmPreference(String restDigestAlgorithmPreference) {
        this.restDigestAlgorithmPreference = restDigestAlgorithmPreference;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.digest.stateCacheEnabled")
    public Boolean getRestDigestStateCacheEnabled() {
        return restDigestStateCacheEnabled;
    }

    public void setRestDigestStateCacheEnabled(Boolean restDigestStateCacheEnabled) {
        this.restDigestStateCacheEnabled = restDigestStateCacheEnabled;
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

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.oauth2.scope")
    public String getRestOAuth2Scope() {
        return restOAuth2Scope;
    }

    public void setRestOAuth2Scope(String restOAuth2Scope) {
        this.restOAuth2Scope = restOAuth2Scope;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.oauth2.clientAuthenticationScheme", allowedValues = {"post", "basic"})
    public String getRestOAuth2ClientAuthenticationScheme() {
        return restOAuth2ClientAuthenticationScheme;
    }

    public void setRestOAuth2ClientAuthenticationScheme(String restOAuth2ClientAuthenticationScheme) {
        this.restOAuth2ClientAuthenticationScheme = restOAuth2ClientAuthenticationScheme;
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
    // REST OAuth2PasswordAuthorization
    // =========================================================================

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.oauth2.username")
    public String getRestOAuth2Username() {
        return restOAuth2Username;
    }

    public void setRestOAuth2Username(String restOAuth2Username) {
        this.restOAuth2Username = restOAuth2Username;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.oauth2.password")
    public GuardedString getRestOAuth2Password() {
        return restOAuth2Password;
    }

    public void setRestOAuth2Password(GuardedString restOAuth2Password) {
        this.restOAuth2Password = restOAuth2Password;
    }

    // =========================================================================
    // REST OAuth2JwtBearerAuthorization / OAuth2SamlAuthorization
    // =========================================================================

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.oauth2.privateKey")
    public GuardedString getRestOAuth2PrivateKey() {
        return restOAuth2PrivateKey;
    }

    public void setRestOAuth2PrivateKey(GuardedString restOAuth2PrivateKey) {
        this.restOAuth2PrivateKey = restOAuth2PrivateKey;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.oauth2.issuer")
    public String getRestOAuth2Issuer() {
        return restOAuth2Issuer;
    }

    public void setRestOAuth2Issuer(String restOAuth2Issuer) {
        this.restOAuth2Issuer = restOAuth2Issuer;
    }

    // =========================================================================
    // REST OAuth2JwtBearerAuthorization
    // =========================================================================

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.oauth2.keyId")
    public String getRestOAuth2KeyId() {
        return restOAuth2KeyId;
    }

    public void setRestOAuth2KeyId(String restOAuth2KeyId) {
        this.restOAuth2KeyId = restOAuth2KeyId;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.oauth2.algorithm", allowedValues = {"RS256", "RS384", "RS512", "PS256", "PS384", "PS512", "ES256", "ES384", "ES512"})
    public String getRestOAuth2Algorithm() {
        return restOAuth2Algorithm;
    }

    public void setRestOAuth2Algorithm(String restOAuth2Algorithm) {
        this.restOAuth2Algorithm = restOAuth2Algorithm;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.oauth2.subject")
    public String getRestOAuth2Subject() {
        return restOAuth2Subject;
    }

    public void setRestOAuth2Subject(String restOAuth2Subject) {
        this.restOAuth2Subject = restOAuth2Subject;
    }

    // =========================================================================
    // REST HawkAuthorization
    // =========================================================================

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.hawk.id")
    public String getRestHawkId() {
        return restHawkId;
    }

    public void setRestHawkId(String restHawkId) {
        this.restHawkId = restHawkId;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.hawk.key")
    public GuardedString getRestHawkKey() {
        return restHawkKey;
    }

    public void setRestHawkKey(GuardedString restHawkKey) {
        this.restHawkKey = restHawkKey;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.hawk.algorithm", allowedValues = {"sha1", "sha256", "sha384", "sha512"})
    public String getRestHawkAlgorithm() {
        return restHawkAlgorithm;
    }

    public void setRestHawkAlgorithm(String restHawkAlgorithm) {
        this.restHawkAlgorithm = restHawkAlgorithm;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.hawk.includePayloadHash")
    public Boolean getRestHawkIncludePayloadHash() {
        return restHawkIncludePayloadHash;
    }

    public void setRestHawkIncludePayloadHash(Boolean restHawkIncludePayloadHash) {
        this.restHawkIncludePayloadHash = restHawkIncludePayloadHash;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.hawk.offset")
    public Integer getRestHawkOffset() {
        return restHawkOffset;
    }

    public void setRestHawkOffset(Integer restHawkOffset) {
        this.restHawkOffset = restHawkOffset;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.hawk.ext")
    public String getRestHawkExt() {
        return restHawkExt;
    }

    public void setRestHawkExt(String restHawkExt) {
        this.restHawkExt = restHawkExt;
    }

    // =========================================================================
    // REST AwsSignatureAuthorization
    // =========================================================================

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.aws.accessKey")
    public String getRestAwsAccessKey() {
        return restAwsAccessKey;
    }

    public void setRestAwsAccessKey(String restAwsAccessKey) {
        this.restAwsAccessKey = restAwsAccessKey;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.aws.secretKey")
    public GuardedString getRestAwsSecretKey() {
        return restAwsSecretKey;
    }

    public void setRestAwsSecretKey(GuardedString restAwsSecretKey) {
        this.restAwsSecretKey = restAwsSecretKey;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.aws.sessionToken")
    public GuardedString getRestAwsSessionToken() {
        return restAwsSessionToken;
    }

    public void setRestAwsSessionToken(GuardedString restAwsSessionToken) {
        this.restAwsSessionToken = restAwsSessionToken;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.aws.region")
    public String getRestAwsRegion() {
        return restAwsRegion;
    }

    public void setRestAwsRegion(String restAwsRegion) {
        this.restAwsRegion = restAwsRegion;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.aws.service")
    public String getRestAwsService() {
        return restAwsService;
    }

    public void setRestAwsService(String restAwsService) {
        this.restAwsService = restAwsService;
    }

    // =========================================================================
    // REST NtlmAuthorization
    // =========================================================================

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.ntlm.username")
    public String getRestNtlmUsername() {
        return restNtlmUsername;
    }

    public void setRestNtlmUsername(String restNtlmUsername) {
        this.restNtlmUsername = restNtlmUsername;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.ntlm.password")
    public GuardedString getRestNtlmPassword() {
        return restNtlmPassword;
    }

    public void setRestNtlmPassword(GuardedString restNtlmPassword) {
        this.restNtlmPassword = restNtlmPassword;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.ntlm.domain")
    public String getRestNtlmDomain() {
        return restNtlmDomain;
    }

    public void setRestNtlmDomain(String restNtlmDomain) {
        this.restNtlmDomain = restNtlmDomain;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.ntlm.workstation")
    public String getRestNtlmWorkstation() {
        return restNtlmWorkstation;
    }

    public void setRestNtlmWorkstation(String restNtlmWorkstation) {
        this.restNtlmWorkstation = restNtlmWorkstation;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.ntlm.version", allowedValues = {"NTLMv1", "NTLMv2"})
    public String getRestNtlmVersion() {
        return restNtlmVersion;
    }

    public void setRestNtlmVersion(String restNtlmVersion) {
        this.restNtlmVersion = restNtlmVersion;
    }

    // =========================================================================
    // SCIM BasicAuthorization
    // =========================================================================

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.basic.username")
    public String getScimUsername() {
        return scimUsername;
    }

    public void setScimUsername(String scimUsername) {
        this.scimUsername = scimUsername;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.basic.password")
    public GuardedString getScimPassword() {
        return scimPassword;
    }

    public void setScimPassword(GuardedString scimPassword) {
        this.scimPassword = scimPassword;
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
    @ConfigurationProperty(displayMessageKey = "scim.token.jwt.algorithm", order = 20, allowedValues = {"HS256", "HS384", "HS512", "RS256", "RS384", "RS512", "PS256", "PS384", "PS512", "ES256", "ES384", "ES512"})
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
    @ConfigurationProperty(displayMessageKey = "scim.token.jwt.location", order = 60, allowedValues = {"header", "query"})
    public String getScimJwtLocation() {
        return scimJwtLocation;
    }

    public void setScimJwtLocation(String scimJwtLocation) {
        this.scimJwtLocation = scimJwtLocation;
    }

    // =========================================================================
    // SCIM ApiKeyAuthorization
    // =========================================================================

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.apikey.value")
    public GuardedString getScimApiKey() {
        return scimApiKey;
    }

    public void setScimApiKey(GuardedString scimApiKey) {
        this.scimApiKey = scimApiKey;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.apikey.name")
    public String getScimApiKeyName() {
        return scimApiKeyName;
    }

    public void setScimApiKeyName(String scimApiKeyName) {
        this.scimApiKeyName = scimApiKeyName;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.apikey.location", allowedValues = {"header", "query"})
    public String getScimApiKeyLocation() {
        return scimApiKeyLocation;
    }

    public void setScimApiKeyLocation(String scimApiKeyLocation) {
        this.scimApiKeyLocation = scimApiKeyLocation;
    }

    // =========================================================================
    // SCIM DigestAuthorization
    // =========================================================================

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.digest.username")
    public String getScimDigestUsername() {
        return scimDigestUsername;
    }

    public void setScimDigestUsername(String scimDigestUsername) {
        this.scimDigestUsername = scimDigestUsername;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.digest.password")
    public GuardedString getScimDigestPassword() {
        return scimDigestPassword;
    }

    public void setScimDigestPassword(GuardedString scimDigestPassword) {
        this.scimDigestPassword = scimDigestPassword;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.digest.autoChallenge")
    public Boolean getScimDigestAutoChallenge() {
        return scimDigestAutoChallenge;
    }

    public void setScimDigestAutoChallenge(Boolean scimDigestAutoChallenge) {
        this.scimDigestAutoChallenge = scimDigestAutoChallenge;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.digest.maxRetries")
    public Integer getScimDigestMaxRetries() {
        return scimDigestMaxRetries;
    }

    public void setScimDigestMaxRetries(Integer scimDigestMaxRetries) {
        this.scimDigestMaxRetries = scimDigestMaxRetries;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.digest.preemptiveAuth")
    public Boolean getScimDigestPreemptiveAuth() {
        return scimDigestPreemptiveAuth;
    }

    public void setScimDigestPreemptiveAuth(Boolean scimDigestPreemptiveAuth) {
        this.scimDigestPreemptiveAuth = scimDigestPreemptiveAuth;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.digest.algorithmPreference", allowedValues = {"MD5", "MD5-sess", "SHA-256", "SHA-256-sess", "SHA-512-256", "SHA-512-256-sess"})
    public String getScimDigestAlgorithmPreference() {
        return scimDigestAlgorithmPreference;
    }

    public void setScimDigestAlgorithmPreference(String scimDigestAlgorithmPreference) {
        this.scimDigestAlgorithmPreference = scimDigestAlgorithmPreference;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.digest.stateCacheEnabled")
    public Boolean getScimDigestStateCacheEnabled() {
        return scimDigestStateCacheEnabled;
    }

    public void setScimDigestStateCacheEnabled(Boolean scimDigestStateCacheEnabled) {
        this.scimDigestStateCacheEnabled = scimDigestStateCacheEnabled;
    }

    // =========================================================================
    // SCIM OAuth2 — shared
    // =========================================================================

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.oauth2.tokenUrl")
    public String getScimOAuth2TokenUrl() {
        return scimOAuth2TokenUrl;
    }

    public void setScimOAuth2TokenUrl(String scimOAuth2TokenUrl) {
        this.scimOAuth2TokenUrl = scimOAuth2TokenUrl;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.oauth2.clientId")
    public String getScimOAuth2ClientId() {
        return scimOAuth2ClientId;
    }

    public void setScimOAuth2ClientId(String scimOAuth2ClientId) {
        this.scimOAuth2ClientId = scimOAuth2ClientId;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.oauth2.scope")
    public String getScimOAuth2Scope() {
        return scimOAuth2Scope;
    }

    public void setScimOAuth2Scope(String scimOAuth2Scope) {
        this.scimOAuth2Scope = scimOAuth2Scope;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.oauth2.clientAuthenticationScheme", allowedValues = {"post", "basic"})
    public String getScimOAuth2ClientAuthenticationScheme() {
        return scimOAuth2ClientAuthenticationScheme;
    }

    public void setScimOAuth2ClientAuthenticationScheme(String scimOAuth2ClientAuthenticationScheme) {
        this.scimOAuth2ClientAuthenticationScheme = scimOAuth2ClientAuthenticationScheme;
    }

    // =========================================================================
    // SCIM OAuth2ClientCredentialsAuthorization
    // =========================================================================

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.oauth2.clientSecret")
    public GuardedString getScimOAuth2ClientSecret() {
        return scimOAuth2ClientSecret;
    }

    public void setScimOAuth2ClientSecret(GuardedString scimOAuth2ClientSecret) {
        this.scimOAuth2ClientSecret = scimOAuth2ClientSecret;
    }

    // =========================================================================
    // SCIM OAuth2PasswordAuthorization
    // =========================================================================

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.oauth2.username")
    public String getScimOAuth2Username() {
        return scimOAuth2Username;
    }

    public void setScimOAuth2Username(String scimOAuth2Username) {
        this.scimOAuth2Username = scimOAuth2Username;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.oauth2.password")
    public GuardedString getScimOAuth2Password() {
        return scimOAuth2Password;
    }

    public void setScimOAuth2Password(GuardedString scimOAuth2Password) {
        this.scimOAuth2Password = scimOAuth2Password;
    }

    // =========================================================================
    // SCIM OAuth2JwtBearerAuthorization / OAuth2SamlAuthorization
    // =========================================================================

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.oauth2.privateKey")
    public GuardedString getScimOAuth2PrivateKey() {
        return scimOAuth2PrivateKey;
    }

    public void setScimOAuth2PrivateKey(GuardedString scimOAuth2PrivateKey) {
        this.scimOAuth2PrivateKey = scimOAuth2PrivateKey;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.oauth2.issuer")
    public String getScimOAuth2Issuer() {
        return scimOAuth2Issuer;
    }

    public void setScimOAuth2Issuer(String scimOAuth2Issuer) {
        this.scimOAuth2Issuer = scimOAuth2Issuer;
    }

    // =========================================================================
    // SCIM OAuth2JwtBearerAuthorization
    // =========================================================================

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.oauth2.keyId")
    public String getScimOAuth2KeyId() {
        return scimOAuth2KeyId;
    }

    public void setScimOAuth2KeyId(String scimOAuth2KeyId) {
        this.scimOAuth2KeyId = scimOAuth2KeyId;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.oauth2.algorithm", allowedValues = {"RS256", "RS384", "RS512", "PS256", "PS384", "PS512", "ES256", "ES384", "ES512"})
    public String getScimOAuth2Algorithm() {
        return scimOAuth2Algorithm;
    }

    public void setScimOAuth2Algorithm(String scimOAuth2Algorithm) {
        this.scimOAuth2Algorithm = scimOAuth2Algorithm;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.oauth2.subject")
    public String getScimOAuth2Subject() {
        return scimOAuth2Subject;
    }

    public void setScimOAuth2Subject(String scimOAuth2Subject) {
        this.scimOAuth2Subject = scimOAuth2Subject;
    }

    // =========================================================================
    // SCIM HawkAuthorization
    // =========================================================================

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.hawk.id")
    public String getScimHawkId() {
        return scimHawkId;
    }

    public void setScimHawkId(String scimHawkId) {
        this.scimHawkId = scimHawkId;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.hawk.key")
    public GuardedString getScimHawkKey() {
        return scimHawkKey;
    }

    public void setScimHawkKey(GuardedString scimHawkKey) {
        this.scimHawkKey = scimHawkKey;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.hawk.algorithm", allowedValues = {"sha1", "sha256", "sha384", "sha512"})
    public String getScimHawkAlgorithm() {
        return scimHawkAlgorithm;
    }

    public void setScimHawkAlgorithm(String scimHawkAlgorithm) {
        this.scimHawkAlgorithm = scimHawkAlgorithm;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.hawk.includePayloadHash")
    public Boolean getScimHawkIncludePayloadHash() {
        return scimHawkIncludePayloadHash;
    }

    public void setScimHawkIncludePayloadHash(Boolean scimHawkIncludePayloadHash) {
        this.scimHawkIncludePayloadHash = scimHawkIncludePayloadHash;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.hawk.offset")
    public Integer getScimHawkOffset() {
        return scimHawkOffset;
    }

    public void setScimHawkOffset(Integer scimHawkOffset) {
        this.scimHawkOffset = scimHawkOffset;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.hawk.ext")
    public String getScimHawkExt() {
        return scimHawkExt;
    }

    public void setScimHawkExt(String scimHawkExt) {
        this.scimHawkExt = scimHawkExt;
    }

    // =========================================================================
    // SCIM AwsSignatureAuthorization
    // =========================================================================

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.aws.accessKey")
    public String getScimAwsAccessKey() {
        return scimAwsAccessKey;
    }

    public void setScimAwsAccessKey(String scimAwsAccessKey) {
        this.scimAwsAccessKey = scimAwsAccessKey;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.aws.secretKey")
    public GuardedString getScimAwsSecretKey() {
        return scimAwsSecretKey;
    }

    public void setScimAwsSecretKey(GuardedString scimAwsSecretKey) {
        this.scimAwsSecretKey = scimAwsSecretKey;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.aws.sessionToken")
    public GuardedString getScimAwsSessionToken() {
        return scimAwsSessionToken;
    }

    public void setScimAwsSessionToken(GuardedString scimAwsSessionToken) {
        this.scimAwsSessionToken = scimAwsSessionToken;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.aws.region")
    public String getScimAwsRegion() {
        return scimAwsRegion;
    }

    public void setScimAwsRegion(String scimAwsRegion) {
        this.scimAwsRegion = scimAwsRegion;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.aws.service")
    public String getScimAwsService() {
        return scimAwsService;
    }

    public void setScimAwsService(String scimAwsService) {
        this.scimAwsService = scimAwsService;
    }

    // =========================================================================
    // SCIM NtlmAuthorization
    // =========================================================================

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.ntlm.username")
    public String getScimNtlmUsername() {
        return scimNtlmUsername;
    }

    public void setScimNtlmUsername(String scimNtlmUsername) {
        this.scimNtlmUsername = scimNtlmUsername;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.ntlm.password")
    public GuardedString getScimNtlmPassword() {
        return scimNtlmPassword;
    }

    public void setScimNtlmPassword(GuardedString scimNtlmPassword) {
        this.scimNtlmPassword = scimNtlmPassword;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.ntlm.domain")
    public String getScimNtlmDomain() {
        return scimNtlmDomain;
    }

    public void setScimNtlmDomain(String scimNtlmDomain) {
        this.scimNtlmDomain = scimNtlmDomain;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.ntlm.workstation")
    public String getScimNtlmWorkstation() {
        return scimNtlmWorkstation;
    }

    public void setScimNtlmWorkstation(String scimNtlmWorkstation) {
        this.scimNtlmWorkstation = scimNtlmWorkstation;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "scim.ntlm.version", allowedValues = {"NTLMv1", "NTLMv2"})
    public String getScimNtlmVersion() {
        return scimNtlmVersion;
    }

    public void setScimNtlmVersion(String scimNtlmVersion) {
        this.scimNtlmVersion = scimNtlmVersion;
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
