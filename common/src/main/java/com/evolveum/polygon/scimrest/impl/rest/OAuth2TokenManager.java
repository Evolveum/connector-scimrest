/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.rest;

import com.evolveum.polygon.scimrest.api.HttpRequestSpecification;
import com.evolveum.polygon.common.GuardedStringAccessor;
import com.evolveum.polygon.scimrest.config.OAuth2GrantType;
import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.config.ScimClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.api.HttpMethod;
import com.evolveum.polygon.scimrest.groovy.api.JwtAssertionBuilder;
import org.identityconnectors.common.security.GuardedString;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorIOException;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the OAuth2 access token lifecycle for a connector instance.
 */
public class OAuth2TokenManager {

    public record OAuth2Config(
            String tokenUrl,
            String clientId,
            GuardedString clientSecret,
            String grantType,
            GuardedString privateKey,
            String scope,
            String clientAuthenticationScheme,
            String username,
            GuardedString password,
            String issuer,
            String keyId,
            String algorithm,
            String subject
    ) {
        public static OAuth2Config from(RestClientConfiguration.OAuth2ClientCredentialsAuthorization conf) {
            return new OAuth2Config(
                    conf.getRestOAuth2TokenUrl(),
                    conf.getRestOAuth2ClientId(),
                    conf.getRestOAuth2ClientSecret(),
                    OAuth2GrantType.CLIENT_CREDENTIALS.getName(),
                    null,
                    conf.getRestOAuth2Scope(),
                    conf.getRestOAuth2ClientAuthenticationScheme(),
                    null, null, null, null, null, null
            );
        }

        public static OAuth2Config from(RestClientConfiguration.OAuth2JwtBearerAuthorization conf) {
            return new OAuth2Config(
                    conf.getRestOAuth2TokenUrl(),
                    conf.getRestOAuth2ClientId(),
                    null,
                    OAuth2GrantType.JWT_BEARER.getName(),
                    conf.getRestOAuth2PrivateKey(),
                    conf.getRestOAuth2Scope(),
                    conf.getRestOAuth2ClientAuthenticationScheme(),
                    null, null,
                    conf.getRestOAuth2Issuer(),
                    conf.getRestOAuth2KeyId(),
                    conf.getRestOAuth2Algorithm(),
                    conf.getRestOAuth2Subject()
            );
        }

        public static OAuth2Config from(RestClientConfiguration.OAuth2PasswordAuthorization conf) {
            return new OAuth2Config(
                    conf.getRestOAuth2TokenUrl(),
                    conf.getRestOAuth2ClientId(),
                    null,
                    OAuth2GrantType.PASSWORD.getName(),
                    null,
                    conf.getRestOAuth2Scope(),
                    conf.getRestOAuth2ClientAuthenticationScheme(),
                    conf.getRestOAuth2Username(),
                    conf.getRestOAuth2Password(),
                    null, null, null, null
            );
        }

        public static OAuth2Config from(ScimClientConfiguration.OAuth2ClientCredentialsAuthorization conf) {
            return new OAuth2Config(
                    conf.getScimOAuth2TokenUrl(),
                    conf.getScimOAuth2ClientId(),
                    conf.getScimOAuth2ClientSecret(),
                    OAuth2GrantType.CLIENT_CREDENTIALS.getName(),
                    null,
                    conf.getScimOAuth2Scope(),
                    conf.getScimOAuth2ClientAuthenticationScheme(),
                    null, null, null, null, null, null
            );
        }

        public static OAuth2Config from(ScimClientConfiguration.OAuth2JwtBearerAuthorization conf) {
            return new OAuth2Config(
                    conf.getScimOAuth2TokenUrl(),
                    conf.getScimOAuth2ClientId(),
                    null,
                    OAuth2GrantType.JWT_BEARER.getName(),
                    conf.getScimOAuth2PrivateKey(),
                    conf.getScimOAuth2Scope(),
                    conf.getScimOAuth2ClientAuthenticationScheme(),
                    null, null,
                    conf.getScimOAuth2Issuer(),
                    conf.getScimOAuth2KeyId(),
                    conf.getScimOAuth2Algorithm(),
                    conf.getScimOAuth2Subject()
            );
        }

        public static OAuth2Config from(ScimClientConfiguration.OAuth2PasswordAuthorization conf) {
            return new OAuth2Config(
                    conf.getScimOAuth2TokenUrl(),
                    conf.getScimOAuth2ClientId(),
                    null,
                    OAuth2GrantType.PASSWORD.getName(),
                    null,
                    conf.getScimOAuth2Scope(),
                    conf.getScimOAuth2ClientAuthenticationScheme(),
                    conf.getScimOAuth2Username(),
                    conf.getScimOAuth2Password(),
                    null, null, null, null
            );
        }

        public static OAuth2Config from(RestClientConfiguration.OAuth2SamlAuthorization conf) {
            return new OAuth2Config(
                    conf.getRestOAuth2TokenUrl(),
                    conf.getRestOAuth2ClientId(),
                    null,
                    OAuth2GrantType.SAML_BEARER.getName(),
                    conf.getRestOAuth2PrivateKey(),
                    conf.getRestOAuth2Scope(),
                    conf.getRestOAuth2ClientAuthenticationScheme(),
                    null, null,
                    conf.getRestOAuth2Issuer(),
                    null, null, null
            );
        }

        public static OAuth2Config from(ScimClientConfiguration.OAuth2SamlAuthorization conf) {
            return new OAuth2Config(
                    conf.getScimOAuth2TokenUrl(),
                    conf.getScimOAuth2ClientId(),
                    null,
                    OAuth2GrantType.SAML_BEARER.getName(),
                    conf.getScimOAuth2PrivateKey(),
                    conf.getScimOAuth2Scope(),
                    conf.getScimOAuth2ClientAuthenticationScheme(),
                    null, null,
                    conf.getScimOAuth2Issuer(),
                    null, null, null
            );
        }
    }

    protected final AuthContext<OAuth2Config> authContext = new AuthContext<>();
    protected final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    protected final ObjectMapper objectMapper = new ObjectMapper();

    private static final Log LOG = Log.getLog(OAuth2TokenManager.class);

    public static final String ACCESS_TOKEN = "access_token";
    public static final String TOKEN_TYPE = "token_type";
    public static final String EXPIRES_IN = "expires_in";
    public static final String EXPIRES_AT = "expires_at";
    public static final String CLIENT_ID = "client_id";
    public static final String CLIENT_SECRET = "client_secret";
    public static final String GRANT_TYPE = "grant_type";

    private static final long JWT_ASSERTION_VALIDITY_SECONDS = 300;

    public void applyToken(OAuth2Config config, HttpRequestSpecification request) {
        authContext.setConfiguration(config);
        ensureValidToken();
        applyTokenToRequest(request);
    }

    protected final void ensureValidToken() {
        if (!validateToken()) {
            synchronized (this) {
                if (!validateToken()) {
                    performTokenRefresh();
                }
            }
        }
    }

    protected boolean validateToken() {
        var accessToken = (String) authContext.get(ACCESS_TOKEN);
        if (accessToken == null) {
            return false;
        }
        Object val = authContext.get(EXPIRES_AT);
        Instant expiresAt = val instanceof Instant i ? i : null;
        return expiresAt == null || Instant.now().isBefore(expiresAt);
    }

    protected void applyTokenToRequest(HttpRequestSpecification request) {
        request.header("Authorization", "Bearer " + authContext.get(ACCESS_TOKEN));
    }

    protected void performTokenRefresh() {
        var config = authContext.getConfiguration();
        LOG.ok("Fetching new OAuth2 access token from {0}", config.tokenUrl());

        var tokenRequest = new HttpRequestSpecification(config.tokenUrl())
                .timeout(Duration.ofSeconds(30))
                .httpMethod(HttpMethod.POST)
                .header("Content-Type", "application/x-www-form-urlencoded");

        customizeBuildTokenRequest(tokenRequest);

        try {
            var response = httpClient.send(
                    new JdkHttpRequestConverter().convert(tokenRequest),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ConnectorIOException(
                        "OAuth2 token request failed with HTTP status " + response.statusCode());
            }
            parseAndStoreTokenResponse(response.body());
        } catch (IOException e) {
            throw new ConnectorIOException("Failed to fetch OAuth2 token: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConnectorIOException("OAuth2 token fetch was interrupted", e);
        }
    }

    protected void parseAndStoreTokenResponse(String responseBody) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);
            processTokenResponse(responseMap);

            Long expiresIn = null;
            Object val = responseMap.get(EXPIRES_IN);
            if (val instanceof Long l) expiresIn = l;
            else if (val instanceof Number n) expiresIn = n.longValue();
            else if (val instanceof String s) {
                try {
                    expiresIn = Long.parseLong(s);
                } catch (NumberFormatException ignored) {
                }
            }

            if (expiresIn != null) {
                Instant expiresAt = Instant.now().plusSeconds(expiresIn);
                authContext.set(EXPIRES_AT, expiresAt);
            }
        } catch (IOException e) {
            throw new ConnectorIOException("Failed to parse token response: " + e.getMessage(), e);
        }
    }

    protected void processTokenResponse(Map<String, Object> response) {
        Object tokenType = response.get(TOKEN_TYPE);
        if (tokenType == null) {
            LOG.warn("OAuth2 token response is missing required ''token_type'' field (RFC 6749 §5.1)");
        } else if (!"bearer".equalsIgnoreCase(tokenType.toString())) {
            throw new ConnectorIOException(
                    "Unsupported OAuth2 token_type: '" + tokenType + "' — only 'bearer' is supported");
        }

        Object accessToken = response.get(ACCESS_TOKEN);
        if (accessToken == null) {
            throw new ConnectorIOException("OAuth2 token response does not contain 'access_token' (RFC 6749 §5.1)");
        }
        authContext.set(ACCESS_TOKEN, accessToken.toString());
    }

    protected void customizeBuildTokenRequest(HttpRequestSpecification request) {
        var config = authContext.getConfiguration();

        switch (OAuth2GrantType.parse(config.grantType())) {
            case JWT_BEARER -> buildJwtBearerRequest(request, config);
            case CLIENT_CREDENTIALS -> buildClientCredentialsRequest(request, config);
            case PASSWORD -> buildPasswordRequest(request, config);
            case SAML_BEARER -> buildSamlBearerRequest(request, config);
        }
    }

    private void buildClientCredentialsRequest(HttpRequestSpecification request, OAuth2Config config) {
        var secretAccessor = new GuardedStringAccessor();
        config.clientSecret().access(secretAccessor);
        String clientSecret = secretAccessor.getClearString();

        if ("basic".equalsIgnoreCase(config.clientAuthenticationScheme())) {
            String credentials = config.clientId() + ":" + clientSecret;
            request.header("Authorization", "Basic " +
                    Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
        } else {
            request.formParam(CLIENT_ID, config.clientId())
                    .formParam(CLIENT_SECRET, clientSecret);
        }

        request.formParam(GRANT_TYPE, OAuth2GrantType.CLIENT_CREDENTIALS.getName());

        if (config.scope() != null && !config.scope().isBlank()) {
            request.formParam("scope", config.scope());
        }
    }

    private void buildPasswordRequest(HttpRequestSpecification request, OAuth2Config config) {
        var passwordAccessor = new GuardedStringAccessor();
        config.password().access(passwordAccessor);
        String password = passwordAccessor.getClearString();

        request.formParam(GRANT_TYPE, OAuth2GrantType.PASSWORD.getName())
                .formParam("username", config.username())
                .formParam("password", password);

        if (config.scope() != null && !config.scope().isBlank()) {
            request.formParam("scope", config.scope());
        }

        if (config.clientId() != null) {
            if ("basic".equalsIgnoreCase(config.clientAuthenticationScheme())) {
                String credentials = config.clientId() + ":";
                request.header("Authorization", "Basic " +
                        Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
            } else {
                request.formParam(CLIENT_ID, config.clientId());
            }
        }
    }

    private void buildJwtBearerRequest(HttpRequestSpecification request, OAuth2Config config) {
        String algorithm = isBlank(config.algorithm()) ? "RS256" : config.algorithm();
        String issuer    = isBlank(config.issuer())    ? config.clientId() : config.issuer();
        String subject   = isBlank(config.subject())   ? config.clientId() : config.subject();

        long now = Instant.now().getEpochSecond();
        JwtAssertionBuilder builder = new JwtAssertionBuilder()
                .claim("iss", issuer)
                .claim("sub", subject)
                .claim("aud", config.tokenUrl())
                .claim("iat", now)
                .claim("exp", now + JWT_ASSERTION_VALIDITY_SECONDS)
                .claim("jti", UUID.randomUUID().toString());

        if (!isBlank(config.keyId())) {
            builder.header("kid", config.keyId());
        }

        String assertion = builder.sign(algorithm, config.privateKey());

        request.formParam(GRANT_TYPE, OAuth2GrantType.JWT_BEARER.getName())
                .formParam("assertion", assertion);

        if (config.scope() != null && !config.scope().isBlank()) {
            request.formParam("scope", config.scope());
        }

        if (config.clientId() != null && !"basic".equalsIgnoreCase(config.clientAuthenticationScheme())) {
            request.formParam(CLIENT_ID, config.clientId());
        }
    }

    private void buildSamlBearerRequest(HttpRequestSpecification request, OAuth2Config config) {
        String issuer = isBlank(config.issuer()) ? config.clientId() : config.issuer();
        String assertion = SamlAssertionBuilder.build(issuer, config.tokenUrl(), config.privateKey());

        request.formParam(GRANT_TYPE, OAuth2GrantType.SAML_BEARER.getName())
                .formParam("assertion", assertion);

        if (config.scope() != null && !config.scope().isBlank()) {
            request.formParam("scope", config.scope());
        }

        if (config.clientId() != null && !"basic".equalsIgnoreCase(config.clientAuthenticationScheme())) {
            request.formParam(CLIENT_ID, config.clientId());
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public AuthContext<OAuth2Config> getAuthContext() {
        return authContext;
    }
}
