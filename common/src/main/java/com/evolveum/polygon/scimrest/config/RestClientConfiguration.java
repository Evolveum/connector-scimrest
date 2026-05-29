/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.config;

import org.identityconnectors.common.security.GuardedString;

public interface RestClientConfiguration extends ConfigurationMixin {

    String getBaseAddress();

    Boolean getTrustAllCertificates();

    String getRestTestEndpoint();

    default Integer getTimeoutSeconds() {
        return 30;
    }

    /**
     *
     * Represents an HTTP Basic Authorization configuration.
     *
     * This configuration extends {@link RestClientConfiguration} and can be used
     * to provide the necessary credentials for performing HTTP Basic Authentication.
     *
     * Should be used only in connectors to systems which does not support Bearer or Token based authorization.
     */
    interface BasicAuthorization extends RestClientConfiguration {

        /** Username for HTTP Basic authentication. */
        String getRestUsername();

        /** Password for HTTP Basic authentication. */
        GuardedString getRestPassword();

    }

    /**
     * Simple Bearer token authorization — sends the token as {@code Authorization: Bearer <value>}.
     */
    interface BearerTokenAuthorization extends RestClientConfiguration {
        GuardedString getRestTokenValue();
    }

    /**
     * JWT Bearer token authorization — generates a signed JWT assertion and sends it as a token.
     * The token name and localization control how the JWT is placed in the request.
     */
    interface JwtBearerAuthorization extends RestClientConfiguration {

        /** Token prefix placed before the JWT value (e.g. {@code Bearer}). */
        String getRestJwtTokenName();

        /** JWT signing algorithm. */
        String getRestJwtAlgorithm();

        /** Secret or private key used to sign the JWT. */
        GuardedString getRestJwtSecret();

        /** When true, {@link #getRestJwtSecret()} is Base64-encoded. */
        Boolean getRestJwtSecretBase64Encoded();

        /** JSON string with custom JWT claims to merge into the payload. */
        String getRestJwtPayload();

        /** Where in the request the token is placed: {@code header} (default) or {@code query}. */
        String getRestJwtLocalization();
    }

    /**
     * HTTP API Key based authorization configuration.
     *
     * The API key is sent as a header or query parameter as specified by {@link #getRestApiKeyLocation()}.
     * Use this for systems that authenticate via a static API key rather than user credentials or tokens.
     */
    interface ApiKeyAuthorization extends RestClientConfiguration {

        /** The API key value. */
        GuardedString getRestApiKey();

        /** Name of the header or query parameter that carries the key (e.g. {@code X-API-Key}, {@code api_key}). */
        String getRestApiKeyName();

        /** Where to place the key: {@code header} or {@code query}. */
        String getRestApiKeyLocation();
    }

    /**
     * HTTP Digest authorization.
     */
    interface DigestAuthorization extends RestClientConfiguration {

        /** Username for Digest authentication. */
        String getRestDigestUsername();

        /** Password for Digest authentication. */
        GuardedString getRestDigestPassword();

        /** When true, automatically handles the Digest challenge/response cycle. */
        Boolean getRestDigestAutoChallenge();

        /** Maximum number of authentication retry attempts. */
        Integer getRestDigestMaxRetries();

        /** When true, sends credentials without waiting for a challenge. */
        Boolean getRestDigestPreemptiveAuth();

        /** Preferred Digest algorithm (e.g. {@code MD5}, {@code SHA-256}). */
        String getRestDigestAlgorithmPreference();

        /** When true, caches nonce state to reduce round-trips. */
        Boolean getRestDigestStateCacheEnabled();
    }

    /**
     * OAuth 2.0 Client Credentials grant authorization.
     * Authenticates with client ID and secret to obtain an access token.
     * Tokens are cached and refreshed automatically when they expire.
     */
    interface OAuth2ClientCredentialsAuthorization extends RestClientConfiguration {

        /** URL of the OAuth 2.0 authorization server token endpoint. */
        String getRestOAuth2TokenUrl();

        /** Client identifier issued by the authorization server. */
        String getRestOAuth2ClientId();

        /** Client secret used to authenticate the client. */
        GuardedString getRestOAuth2ClientSecret();

        /** Optional space-separated list of requested scopes. */
        String getRestOAuth2Scope();

        /** Optional audience value sent to the token endpoint. */
        String getRestOAuth2Audience();

        /** Name of the JSON field in the token response that contains the access token (default: {@code access_token}). */
        String getRestOAuth2TokenName();

        /** How the client credentials are sent: {@code post} (body) or {@code basic} (header). */
        String getRestOAuth2ClientAuthenticationScheme();
    }

    /**
     * OAuth 2.0 Resource Owner Password Credentials grant authorization.
     */
    interface OAuth2PasswordAuthorization extends RestClientConfiguration {

        /** URL of the OAuth 2.0 authorization server token endpoint. */
        String getRestOAuth2TokenUrl();

        /** Client identifier issued by the authorization server. */
        String getRestOAuth2ClientId();

        /** Resource owner's username. */
        String getRestOAuth2Username();

        /** Resource owner's password. */
        GuardedString getRestOAuth2Password();

        /** Optional space-separated list of requested scopes. */
        String getRestOAuth2Scope();

        /** Optional audience value sent to the token endpoint. */
        String getRestOAuth2Audience();

        /** Name of the JSON field in the token response that contains the access token (default: {@code access_token}). */
        String getRestOAuth2TokenName();

        /** How the client credentials are sent: {@code post} (body) or {@code basic} (header). */
        String getRestOAuth2ClientAuthenticationScheme();
    }

    /**
     * OAuth 2.0 JWT Bearer grant authorization (RFC 7523).
     * Authenticates by signing a JWT assertion with a private key; no client secret needed.
     * Tokens are cached and refreshed automatically when they expire.
     */
    interface OAuth2JwtBearerAuthorization extends RestClientConfiguration {

        /** URL of the OAuth 2.0 authorization server token endpoint. */
        String getRestOAuth2TokenUrl();

        /**
         * Client identifier — used as the {@code client_id} parameter and as the
         * {@code iss}/{@code sub} claims in the JWT assertion.
         */
        String getRestOAuth2ClientId();

        /** PEM-encoded PKCS#8 private key used to sign the JWT assertion. */
        GuardedString getRestOAuth2PrivateKey();

        /** Issuer claim ({@code iss}) in the JWT assertion; defaults to the client ID if blank. */
        String getRestOAuth2Issuer();

        /** Optional key ID ({@code kid}) header in the JWT. */
        String getRestOAuth2KeyId();

        /** Signing algorithm (e.g. {@code RS256}). */
        String getRestOAuth2Algorithm();

        /** Subject claim ({@code sub}) in the JWT assertion. */
        String getRestOAuth2Subject();

        /** Optional space-separated list of requested scopes. */
        String getRestOAuth2Scope();

        /** Optional audience for both the JWT and the token request. */
        String getRestOAuth2Audience();

        /** Name of the JSON field in the token response that contains the access token. */
        String getRestOAuth2TokenName();

        /** How the client assertion is sent to the token endpoint. */
        String getRestOAuth2ClientAuthenticationScheme();
    }

    /**
     * OAuth 2.0 SAML Bearer grant authorization (RFC 7522).
     * Authenticates using a SAML assertion signed with a private key.
     */
    interface OAuth2SamlAuthorization extends RestClientConfiguration {

        /** URL of the OAuth 2.0 authorization server token endpoint. */
        String getRestOAuth2TokenUrl();

        /** Client identifier issued by the authorization server. */
        String getRestOAuth2ClientId();

        /** PEM-encoded PKCS#8 private key used to sign the SAML assertion. */
        GuardedString getRestOAuth2PrivateKey();

        /** Issuer ({@code Issuer} element) of the SAML assertion. */
        String getRestOAuth2Issuer();

        /** Optional space-separated list of requested scopes. */
        String getRestOAuth2Scope();

        /** Optional audience ({@code AudienceRestriction}) in the SAML assertion. */
        String getRestOAuth2Audience();

        /** Name of the JSON field in the token response that contains the access token. */
        String getRestOAuth2TokenName();

        /** How the SAML assertion is sent to the token endpoint. */
        String getRestOAuth2ClientAuthenticationScheme();
    }

    /**
     * Hawk authentication (MAC-based HTTP request signing).
     */
    interface HawkAuthorization extends RestClientConfiguration {

        /** Hawk credential identifier (the key ID shared with the server). */
        String getRestHawkId();

        /** Hawk secret key used to compute the MAC. */
        GuardedString getRestHawkKey();

        /** HMAC algorithm used for signing (e.g. {@code sha256}). */
        String getRestHawkAlgorithm();

        /** When true, includes a hash of the request body in the signature. */
        Boolean getRestHawkIncludePayloadHash();

        /** Optional clock offset in seconds to compensate for time drift. */
        Integer getRestHawkOffset();

        /** Optional application-specific extension string included in the signature. */
        String getRestHawkExt();
    }

    /**
     * AWS Signature Version 4 (or 2) request signing.
     */
    interface AwsSignatureAuthorization extends RestClientConfiguration {

        /** AWS access key ID. */
        String getRestAwsAccessKey();

        /** AWS secret access key. */
        GuardedString getRestAwsSecretKey();

        /** AWS region (e.g. {@code us-east-1}). */
        String getRestAwsRegion();

        /** AWS service name (e.g. {@code execute-api}). */
        String getRestAwsService();

        /** Optional temporary session token for STS credentials. */
        GuardedString getRestAwsSessionToken();

        /** Signature version: {@code 4} (default) or {@code 2} (legacy). */
        String getRestAwsSignatureVersion();
    }

    /**
     * NTLM (Windows) authentication.
     */
    interface NtlmAuthorization extends RestClientConfiguration {

        /** Windows username for NTLM authentication. */
        String getRestNtlmUsername();

        /** Windows password for NTLM authentication. */
        GuardedString getRestNtlmPassword();

        /** Windows domain name. */
        String getRestNtlmDomain();

        /** Optional workstation name sent in the NTLM negotiation. */
        String getRestNtlmWorkstation();

        /** NTLM protocol version (e.g. {@code NTLMv2}). */
        String getRestNtlmVersion();
    }


    static boolean isConfigured(Class<? extends RestClientConfiguration> type, RestClientConfiguration configuration) {
        if (NtlmAuthorization.class.isAssignableFrom(type)) {
            return configuration instanceof NtlmAuthorization o
                    && o.getRestNtlmUsername() != null && o.getRestNtlmPassword() != null;
        }
        if (AwsSignatureAuthorization.class.isAssignableFrom(type)) {
            return configuration instanceof AwsSignatureAuthorization o
                    && o.getRestAwsAccessKey() != null && o.getRestAwsSecretKey() != null
                    && o.getRestAwsRegion() != null && o.getRestAwsService() != null;
        }
        if (HawkAuthorization.class.isAssignableFrom(type)) {
            return configuration instanceof HawkAuthorization o
                    && o.getRestHawkId() != null && o.getRestHawkKey() != null;
        }
        if (OAuth2SamlAuthorization.class.isAssignableFrom(type)) {
            return configuration instanceof OAuth2SamlAuthorization o
                    && o.getRestOAuth2TokenUrl() != null && o.getRestOAuth2ClientId() != null
                    && o.getRestOAuth2PrivateKey() != null && o.getRestOAuth2Issuer() != null;
        }
        if (OAuth2JwtBearerAuthorization.class.isAssignableFrom(type)) {
            return configuration instanceof OAuth2JwtBearerAuthorization o
                    && o.getRestOAuth2TokenUrl() != null && o.getRestOAuth2ClientId() != null
                    && o.getRestOAuth2PrivateKey() != null;
        }
        if (OAuth2PasswordAuthorization.class.isAssignableFrom(type)) {
            return configuration instanceof OAuth2PasswordAuthorization o
                    && o.getRestOAuth2TokenUrl() != null && o.getRestOAuth2ClientId() != null
                    && o.getRestOAuth2Username() != null && o.getRestOAuth2Password() != null;
        }
        if (OAuth2ClientCredentialsAuthorization.class.isAssignableFrom(type)) {
            return configuration instanceof OAuth2ClientCredentialsAuthorization o
                    && o.getRestOAuth2TokenUrl() != null && o.getRestOAuth2ClientId() != null
                    && o.getRestOAuth2ClientSecret() != null;
        }
        if (DigestAuthorization.class.isAssignableFrom(type)) {
            return configuration instanceof DigestAuthorization o
                    && o.getRestDigestUsername() != null && o.getRestDigestPassword() != null;
        }
        if (ApiKeyAuthorization.class.isAssignableFrom(type)) {
            return configuration instanceof ApiKeyAuthorization api
                    && api.getRestApiKey() != null && api.getRestApiKeyName() != null;
        }
        if (JwtBearerAuthorization.class.isAssignableFrom(type)) {
            return configuration instanceof JwtBearerAuthorization jwt
                    && jwt.getRestJwtAlgorithm() != null && jwt.getRestJwtSecret() != null;
        }
        if (BearerTokenAuthorization.class.isAssignableFrom(type)) {
            return configuration instanceof BearerTokenAuthorization token
                    && token.getRestTokenValue() != null;
        }
        if (BasicAuthorization.class.isAssignableFrom(type)) {
            return configuration instanceof BasicAuthorization basic
                    && basic.getRestUsername() != null && basic.getRestPassword() != null;
        }
        return false;
    }
}
