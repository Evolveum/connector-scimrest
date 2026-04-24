/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.rest;

import com.evolveum.polygon.scimrest.api.HttpRequestDTO;
import com.evolveum.polygon.common.GuardedStringAccessor;
import com.evolveum.polygon.scimrest.config.OAuth2GrantType;
import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.api.HttpMethod;
import com.evolveum.polygon.scimrest.groovy.api.JwtAssertionBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorIOException;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the OAuth2 access token lifecycle for a connector instance.
 *
 * <h2>Token fetch flow</h2>
 * <ol>
 *   <li>{@link #validateToken()}            — decides whether the cached token is still usable.</li>
 *   <li>{@link #customizeBuildTokenRequest} — builds the grant-type-specific token request.</li>
 *   <li>{@link #processTokenResponse}       — extracts token and expiry from the server response.</li>
 *   <li>{@link #applyTokenToRequest}        — attaches the token to every outgoing API request.</li>
 * </ol>
 *
 * <p>Thread-safe: token refresh is synchronized so only one thread fetches a new token at a time.
 */
public class OAuth2TokenManager {

    private static final Log LOG = Log.getLog(OAuth2TokenManager.class);
    private static final long JWT_ASSERTION_VALIDITY_SECONDS = 300;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OAuth2Context oauth2Context = new OAuth2Context();

    public OAuth2TokenManager() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public OAuth2Context getOauth2Context() {
        return oauth2Context;
    }

    // -------------------------------------------------------------------------

    public void applyToken(RestClientConfiguration.OAuth2Authorization config,
                           HttpRequestDTO request) {
        oauth2Context.setConfiguration(config);
        ensureValidToken(config);
        applyTokenToRequest(request);
    }

    private void ensureValidToken(RestClientConfiguration.OAuth2Authorization config) {
        if (!validateToken()) {
            synchronized (this) {
                if (!validateToken()) {
                    refreshToken(config);
                }
            }
        }
    }

    protected boolean validateToken() {
        var accessToken = oauth2Context.accessToken();
        if (accessToken == null) {
            return false;
        }
        Instant expiresAt = oauth2Context.expiresAt();
        return expiresAt == null || Instant.now().isBefore(expiresAt);
    }

    private void refreshToken(RestClientConfiguration.OAuth2Authorization config) {
        LOG.ok("Fetching new OAuth2 access token from {0}", config.getRestOAuth2TokenUrl());

        var tokenRequest = new HttpRequestDTO(config.getRestOAuth2TokenUrl())
            .timeout(Duration.ofSeconds(30))
            .httpMethod(HttpMethod.POST)
            .header("Content-Type", "application/x-www-form-urlencoded");

        customizeBuildTokenRequest(tokenRequest);

        try {
            var response = httpClient.send(new JdkHttpRequestConverter().convert(tokenRequest), HttpResponse.BodyHandlers.ofString());
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

    protected void customizeBuildTokenRequest(HttpRequestDTO request) {
        var config = oauth2Context.getConfiguration();

        switch (OAuth2GrantType.parse(config.getRestOAuth2GrantType())) {
            case JWT_BEARER -> buildJwtBearerRequest(request, config);
            case CLIENT_CREDENTIALS -> buildClientCredentialsRequest(request, config);
        }
    }

    private void buildClientCredentialsRequest(HttpRequestDTO request,
                                               RestClientConfiguration.OAuth2Authorization config) {
        var secretAccessor = new GuardedStringAccessor();
        config.getRestOAuth2ClientSecret().access(secretAccessor);
        String clientSecret = secretAccessor.getClearString();

        request.formParam(OAuth2Context.CLIENT_ID, config.getRestOAuth2ClientId())
               .formParam(OAuth2Context.CLIENT_SECRET, clientSecret);

        String existingRefreshToken = oauth2Context.refreshToken();
        if (existingRefreshToken != null) {
            LOG.ok("Using refresh_token grant to obtain new access token");
            oauth2Context.set(OAuth2Context.REFRESH_TOKEN, null);
            request.formParam(OAuth2Context.GRANT_TYPE, OAuth2Context.REFRESH_TOKEN);
            request.formParam(OAuth2Context.REFRESH_TOKEN, existingRefreshToken);
        } else {
            request.formParam(OAuth2Context.GRANT_TYPE, OAuth2GrantType.CLIENT_CREDENTIALS.getName());
        }
    }

    private void buildJwtBearerRequest(HttpRequestDTO request,
                                       RestClientConfiguration.OAuth2Authorization config) {
        LOG.ok("Building JWT Bearer assertion for client {0}", config.getRestOAuth2ClientId());
        long now = Instant.now().getEpochSecond();
        String assertion = new JwtAssertionBuilder()
                .claim("iss", config.getRestOAuth2ClientId())
                .claim("sub", config.getRestOAuth2ClientId())
                .claim("aud", config.getRestOAuth2TokenUrl())
                .claim("iat", now)
                .claim("exp", now + JWT_ASSERTION_VALIDITY_SECONDS)
                .claim("jti", UUID.randomUUID().toString())
                .sign(config.getRestOAuth2PrivateKey(), "SHA256withRSA", "RSA");

        request.formParam(OAuth2Context.GRANT_TYPE, OAuth2GrantType.JWT_BEARER.getName())
               .formParam("client_id", config.getRestOAuth2ClientId())
               .formParam("assertion", assertion);
    }

    private void parseAndStoreTokenResponse(String responseBody) {
        try {
            Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);
            processTokenResponse(responseMap);
        } catch (IOException e) {
            throw new ConnectorIOException("Failed to parse OAuth2 token response", e);
        }
        Long expiresIn = oauth2Context.expiresIn();
        if (expiresIn != null) {
            Instant expiresAt = Instant.now().plusSeconds(expiresIn);
            oauth2Context.set(OAuth2Context.EXPIRES_AT, expiresAt);
            LOG.ok("OAuth2 token expires at {0} (expires_in={1}s)", expiresAt, expiresIn);
        } else {
            LOG.ok("OAuth2 token has no expiry, treating as always valid");
        }
    }

    protected void processTokenResponse(Map<String, Object> response) {
        Object accessToken = response.get(OAuth2Context.ACCESS_TOKEN);
        if (accessToken == null) {
            throw new ConnectorIOException("OAuth2 token response does not contain 'access_token'");
        }
        oauth2Context.set(OAuth2Context.ACCESS_TOKEN, accessToken.toString());
        oauth2Context.set(OAuth2Context.EXPIRES_IN, response.get(OAuth2Context.EXPIRES_IN) instanceof Number n ? n.longValue() : null);
        Object refreshToken = response.get(OAuth2Context.REFRESH_TOKEN);
        if (refreshToken != null) {
            oauth2Context.set(OAuth2Context.REFRESH_TOKEN, refreshToken.toString());
        }
    }

    protected void applyTokenToRequest(HttpRequestDTO request) {
        request.header("Authorization", "Bearer " + oauth2Context.accessToken());
    }
}
