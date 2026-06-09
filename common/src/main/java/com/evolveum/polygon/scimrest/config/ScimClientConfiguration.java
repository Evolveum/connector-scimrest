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

    /**
     * HTTP Basic authorization — legacy interface, prefer {@link BasicAuthorization}.
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc7617">RFC 7617 — The 'Basic' HTTP Authentication Scheme</a>
     */
    interface HttpBasic extends ScimClientConfiguration {

        /** Username for HTTP Basic authentication. */
        String getScimUsername();

        /** Password for HTTP Basic authentication. */
        GuardedString getScimPassword();
    }

    /**
     * Alias for {@link HttpBasic} — mirrors the REST naming convention.
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc7617">RFC 7617 — The 'Basic' HTTP Authentication Scheme</a>
     */
    interface BasicAuthorization extends ScimClientConfiguration {

        /** Username for HTTP Basic authentication. */
        String getScimUsername();

        /** Password for HTTP Basic authentication. */
        GuardedString getScimPassword();
    }

    /**
     * Simple Bearer token authorization — sends the token as {@code Authorization: Bearer <value>}.
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc6750">RFC 6750 — The OAuth 2.0 Authorization Framework: Bearer Token Usage</a>
     */
    interface BearerTokenAuthorization extends ScimClientConfiguration {
        GuardedString getScimTokenValue();
    }

    /**
     * JWT Bearer token authorization — generates a signed JWT assertion and sends it as a token.
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc7519">RFC 7519 — JSON Web Token (JWT)</a>
     * @see <a href="https://www.rfc-editor.org/rfc/rfc7515">RFC 7515 — JSON Web Signature (JWS)</a>
     */
    interface JwtBearerAuthorization extends ScimClientConfiguration {

        /** Token prefix placed before the JWT value (e.g. {@code Bearer}). */
        String getScimJwtTokenName();

        /** JWT signing algorithm. */
        String getScimJwtAlgorithm();

        /** Secret or private key used to sign the JWT. */
        GuardedString getScimJwtSecret();

        /** When true, {@link #getScimJwtSecret()} is Base64-encoded. */
        Boolean getScimJwtSecretBase64Encoded();

        /** JSON string with custom JWT claims to merge into the payload. */
        String getScimJwtPayload();

        /** Where in the request the token is placed: {@code header} (default) or {@code query}. */
        String getScimJwtLocalization();
    }

    /**
     * HTTP API Key based authorization — sends the key in a header or query parameter.
     * There is no formal standard; this follows common industry convention.
     */
    interface ApiKeyAuthorization extends ScimClientConfiguration {

        /** The API key value. */
        GuardedString getScimApiKey();

        /** Name of the header or query parameter that carries the key (e.g. {@code X-API-Key}, {@code api_key}). */
        String getScimApiKeyName();

        /** Where to place the key: {@code header} or {@code query}. */
        String getScimApiKeyLocation();

    }

    /**
     * HTTP Digest authorization.
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc7616">RFC 7616 — HTTP Digest Access Authentication</a>
     */
    interface DigestAuthorization extends ScimClientConfiguration {

        /** Username for Digest authentication. */
        String getScimDigestUsername();

        /** Password for Digest authentication. */
        GuardedString getScimDigestPassword();

        /** When true, automatically handles the Digest challenge/response cycle. */
        Boolean getScimDigestAutoChallenge();

        /** Maximum number of authentication retry attempts. */
        Integer getScimDigestMaxRetries();

        /** When true, sends credentials without waiting for a challenge. */
        Boolean getScimDigestPreemptiveAuth();

        /** Preferred Digest algorithm (e.g. {@code MD5}, {@code SHA-256}). */
        String getScimDigestAlgorithmPreference();

        /** When true, caches nonce state to reduce round-trips. */
        Boolean getScimDigestStateCacheEnabled();
    }

    /**
     * OAuth 2.0 Client Credentials grant authorization.
     * Authenticates with client ID and secret to obtain an access token.
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-4.4">RFC 6749 §4.4 — Client Credentials Grant</a>
     */
    interface OAuth2ClientCredentialsAuthorization extends ScimClientConfiguration {

        /** URL of the OAuth 2.0 authorization server token endpoint. */
        String getScimOAuth2TokenUrl();

        /** Client identifier issued by the authorization server. */
        String getScimOAuth2ClientId();

        /** Client secret used to authenticate the client. */
        GuardedString getScimOAuth2ClientSecret();

        /** Optional space-separated list of requested scopes. */
        String getScimOAuth2Scope();

        /** How the client credentials are sent: {@code post} (body) or {@code basic} (header). */
        String getScimOAuth2ClientAuthenticationScheme();
    }

    /**
     * OAuth 2.0 Resource Owner Password Credentials grant authorization.
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-4.3">RFC 6749 §4.3 — Resource Owner Password Credentials Grant</a>
     */
    interface OAuth2PasswordAuthorization extends ScimClientConfiguration {

        /** URL of the OAuth 2.0 authorization server token endpoint. */
        String getScimOAuth2TokenUrl();

        /** Client identifier issued by the authorization server. */
        String getScimOAuth2ClientId();

        /** Resource owner's username. */
        String getScimOAuth2Username();

        /** Resource owner's password. */
        GuardedString getScimOAuth2Password();

        /** Optional space-separated list of requested scopes. */
        String getScimOAuth2Scope();

        /** How the client credentials are sent: {@code post} (body) or {@code basic} (header). */
        String getScimOAuth2ClientAuthenticationScheme();
    }

    /**
     * OAuth 2.0 JWT Bearer grant authorization.
     * Authenticates by signing a JWT assertion with a private key; no client secret needed.
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc7523">RFC 7523 — JWT Profile for OAuth 2.0 Client Authentication and Authorization Grants</a>
     */
    interface OAuth2JwtBearerAuthorization extends ScimClientConfiguration {

        /** URL of the OAuth 2.0 authorization server token endpoint. */
        String getScimOAuth2TokenUrl();

        /**
         * Client identifier — used as the {@code client_id} parameter and as the
         * {@code iss}/{@code sub} claims in the JWT assertion.
         */
        String getScimOAuth2ClientId();

        /** PEM-encoded PKCS#8 private key used to sign the JWT assertion. */
        GuardedString getScimOAuth2PrivateKey();

        /** Issuer claim ({@code iss}) in the JWT assertion; defaults to the client ID if blank. */
        String getScimOAuth2Issuer();

        /** Optional key ID ({@code kid}) header in the JWT. */
        String getScimOAuth2KeyId();

        /** Signing algorithm (e.g. {@code RS256}). */
        String getScimOAuth2Algorithm();

        /** Subject claim ({@code sub}) in the JWT assertion. */
        String getScimOAuth2Subject();

        /** Optional space-separated list of requested scopes. */
        String getScimOAuth2Scope();

        /** How the client assertion is sent to the token endpoint. */
        String getScimOAuth2ClientAuthenticationScheme();
    }

    /**
     * OAuth 2.0 SAML Bearer grant authorization.
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc7522">RFC 7522 — SAML Profile for OAuth 2.0 Client Authentication and Authorization Grants</a>
     */
    interface OAuth2SamlAuthorization extends ScimClientConfiguration {

        /** URL of the OAuth 2.0 authorization server token endpoint. */
        String getScimOAuth2TokenUrl();

        /** Client identifier issued by the authorization server. */
        String getScimOAuth2ClientId();

        /** PEM-encoded PKCS#8 private key used to sign the SAML assertion. */
        GuardedString getScimOAuth2PrivateKey();

        /** Issuer ({@code Issuer} element) of the SAML assertion. */
        String getScimOAuth2Issuer();

        /** Optional space-separated list of requested scopes. */
        String getScimOAuth2Scope();

        /** How the SAML assertion is sent to the token endpoint. */
        String getScimOAuth2ClientAuthenticationScheme();
    }

    /**
     * Hawk authentication (MAC-based HTTP request signing).
     *
     * @see <a href="https://github.com/mozilla/hawk">Hawk — HTTP Holder-Of-Key Authentication Scheme</a>
     */
    interface HawkAuthorization extends ScimClientConfiguration {

        /** Hawk credential identifier (the key ID shared with the server). */
        String getScimHawkId();

        /** Hawk secret key used to compute the MAC. */
        GuardedString getScimHawkKey();

        /** HMAC algorithm used for signing (e.g. {@code sha256}). */
        String getScimHawkAlgorithm();

        /** When true, includes a hash of the request body in the signature. */
        Boolean getScimHawkIncludePayloadHash();

        /** Optional clock offset in seconds to compensate for time drift. */
        Integer getScimHawkOffset();

        /** Optional application-specific extension string included in the signature. */
        String getScimHawkExt();
    }

    /**
     * AWS Signature Version 4 (or 2) request signing.
     *
     * @see <a href="https://docs.aws.amazon.com/general/latest/gr/signature-version-4.html">AWS Signature Version 4</a>
     * @see <a href="https://docs.aws.amazon.com/general/latest/gr/signature-version-2.html">AWS Signature Version 2 (legacy)</a>
     */
    interface AwsSignatureAuthorization extends ScimClientConfiguration {

        /** AWS access key ID. */
        String getScimAwsAccessKey();

        /** AWS secret access key. */
        GuardedString getScimAwsSecretKey();

        /** AWS region (e.g. {@code us-east-1}). */
        String getScimAwsRegion();

        /** AWS service name (e.g. {@code execute-api}). */
        String getScimAwsService();

        /** Optional temporary session token for STS credentials. */
        GuardedString getScimAwsSessionToken();

        /** Signature version: {@code 4} (default) or {@code 2} (legacy). */
        String getScimAwsSignatureVersion();
    }

    /**
     * NTLM (Windows) authentication.
     *
     * @see <a href="https://learn.microsoft.com/en-us/openspecs/windows_protocols/ms-nlmp/">MS-NLMP — NT LAN Manager (NTLM) Authentication Protocol</a>
     */
    interface NtlmAuthorization extends ScimClientConfiguration {

        /** Windows username for NTLM authentication. */
        String getScimNtlmUsername();

        /** Windows password for NTLM authentication. */
        GuardedString getScimNtlmPassword();

        /** Windows domain name. */
        String getScimNtlmDomain();

        /** Optional workstation name sent in the NTLM negotiation. */
        String getScimNtlmWorkstation();

        /** NTLM protocol version (e.g. {@code NTLMv2}). */
        String getScimNtlmVersion();
    }

    static boolean isConfigured(Class<? extends ScimClientConfiguration> type, ScimClientConfiguration configuration) {
        if (NtlmAuthorization.class.isAssignableFrom(type)) {
            return configuration instanceof NtlmAuthorization o
                    && o.getScimNtlmUsername() != null && o.getScimNtlmPassword() != null;
        }
        if (AwsSignatureAuthorization.class.isAssignableFrom(type)) {
            return configuration instanceof AwsSignatureAuthorization o
                    && o.getScimAwsAccessKey() != null && o.getScimAwsSecretKey() != null
                    && o.getScimAwsRegion() != null && o.getScimAwsService() != null;
        }
        if (HawkAuthorization.class.isAssignableFrom(type)) {
            return configuration instanceof HawkAuthorization o
                    && o.getScimHawkId() != null && o.getScimHawkKey() != null;
        }
        if (OAuth2SamlAuthorization.class.isAssignableFrom(type)) {
            return configuration instanceof OAuth2SamlAuthorization o
                    && o.getScimOAuth2TokenUrl() != null && o.getScimOAuth2ClientId() != null
                    && o.getScimOAuth2PrivateKey() != null;
        }
        if (OAuth2JwtBearerAuthorization.class.isAssignableFrom(type)) {
            return configuration instanceof OAuth2JwtBearerAuthorization o
                    && o.getScimOAuth2TokenUrl() != null && o.getScimOAuth2ClientId() != null
                    && o.getScimOAuth2PrivateKey() != null;
        }
        if (OAuth2PasswordAuthorization.class.isAssignableFrom(type)) {
            return configuration instanceof OAuth2PasswordAuthorization o
                    && o.getScimOAuth2TokenUrl() != null && o.getScimOAuth2ClientId() != null
                    && o.getScimOAuth2Username() != null && o.getScimOAuth2Password() != null;
        }
        if (OAuth2ClientCredentialsAuthorization.class.isAssignableFrom(type)) {
            return configuration instanceof OAuth2ClientCredentialsAuthorization o
                    && o.getScimOAuth2TokenUrl() != null && o.getScimOAuth2ClientId() != null
                    && o.getScimOAuth2ClientSecret() != null;
        }
        if (DigestAuthorization.class.isAssignableFrom(type)) {
            return configuration instanceof DigestAuthorization o
                    && o.getScimDigestUsername() != null && o.getScimDigestPassword() != null;
        }
        if (ApiKeyAuthorization.class.isAssignableFrom(type)) {
            return configuration instanceof ApiKeyAuthorization api
                    && api.getScimApiKey() != null && api.getScimApiKeyName() != null;
        }
        if (JwtBearerAuthorization.class.isAssignableFrom(type)) {
            return configuration instanceof JwtBearerAuthorization jwt
                    && jwt.getScimJwtAlgorithm() != null && jwt.getScimJwtSecret() != null;
        }
        if (BearerTokenAuthorization.class.isAssignableFrom(type)) {
            return configuration instanceof BearerTokenAuthorization token
                    && token.getScimTokenValue() != null;
        }
        if (BasicAuthorization.class.isAssignableFrom(type)) {
            return configuration instanceof BasicAuthorization basic
                    && basic.getScimUsername() != null && basic.getScimPassword() != null;
        }
        if (HttpBasic.class.isAssignableFrom(type)) {
            return configuration instanceof HttpBasic basic
                    && basic.getScimUsername() != null && basic.getScimPassword() != null;
        }
        return false;
    }

}
