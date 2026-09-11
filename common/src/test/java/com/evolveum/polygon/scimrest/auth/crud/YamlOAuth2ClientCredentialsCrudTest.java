/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.auth.crud;

import com.evolveum.polygon.conndev.spi.ClassHandlerConnectorBase;
import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.support.AbstractCrudConnectorTest;
import com.evolveum.polygon.scimrest.support.YamlOperationsConnector;
import org.identityconnectors.common.security.GuardedString;
import org.testng.annotations.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.assertEquals;

/**
 * YAML OAuth2 client-credentials customization: the {@code applyToken} hook (a Groovy block scalar
 * receiving {@code request} with the OAuth2 context as delegate) applies the obtained token to
 * outgoing CRUD requests.
 */
public class YamlOAuth2ClientCredentialsCrudTest extends AbstractCrudConnectorTest {

    private static final String TOKEN_ENDPOINT = "/oauth2/token";
    private static final String ACCESS_TOKEN = "yaml-cc-token";
    private static final String CUSTOM_HEADER = "X-Yaml-Token";

    private static final String AUTH_YAML = """
            authentication:
              rest:
                oauth2ClientCredentials:
                  applyToken: |
                    request.header("X-Yaml-Token", access_token)
            """;

    @Test
    public void oauth2TokenAppliedToSearch() {
        wireMockServer.stubFor(post(urlEqualTo(TOKEN_ENDPOINT))
                .willReturn(okJson("{\"access_token\":\"" + ACCESS_TOKEN + "\",\"token_type\":\"Bearer\",\"expires_in\":3600}")));
        stubSearchAccounts();

        searchAccounts(initConnectorWithAuth(AUTH_YAML));

        assertEquals(wireMockServer.findAll(getRequestedFor(urlEqualTo(ACCOUNTS_PATH))
                .withHeader(CUSTOM_HEADER, equalTo(ACCESS_TOKEN))).size(), 1);
    }

    /**
     * {@code buildTokenRequest} customizes the outgoing token request (replacing the default
     * entirely — supplying the hook means the built-in grant_type/client_id/client_secret population
     * is skipped, so the hook must set what it needs); {@code parseTokenResponse} extracts the token
     * from a non-standard response shape via {@code set("access_token", ...)} on the auth context
     * (the closure's delegate). The default {@code applyToken} then applies it as a Bearer header.
     */
    @Test
    public void buildAndParseTokenRequestHooksDriveDefaultApplyToken() {
        wireMockServer.stubFor(post(urlEqualTo(TOKEN_ENDPOINT))
                .willReturn(okJson("{\"data\":{\"token\":\"nested-token\"}}")));
        stubSearchAccounts();

        var authYaml = """
                authentication:
                  rest:
                    oauth2ClientCredentials:
                      buildTokenRequest: |
                        request.formParam("grant_type", "client_credentials")
                        request.formParam("custom_param", "extra-value")
                      parseTokenResponse: |
                        set("access_token", response.data.token)
                """;

        searchAccounts(initConnectorWithAuth(authYaml));

        assertEquals(wireMockServer.findAll(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))
                .withRequestBody(containing("custom_param=extra-value"))).size(), 1);
        assertEquals(wireMockServer.findAll(getRequestedFor(urlEqualTo(ACCOUNTS_PATH))
                .withHeader("Authorization", equalTo("Bearer nested-token"))).size(), 1);
    }

    /** {@code validateToken} takes no argument (only the auth-context delegate); always returning
     * {@code false} forces a token refetch on every request. */
    @Test
    public void validateTokenHookForcesRefetchOnEveryRequest() {
        wireMockServer.stubFor(post(urlEqualTo(TOKEN_ENDPOINT))
                .willReturn(okJson("{\"access_token\":\"" + ACCESS_TOKEN + "\",\"token_type\":\"Bearer\",\"expires_in\":3600}")));
        stubSearchAccounts();

        var authYaml = """
                authentication:
                  rest:
                    oauth2ClientCredentials:
                      validateToken: |
                        false
                """;

        var connector = initConnectorWithAuth(authYaml);
        searchAccounts(connector);
        searchAccounts(connector);

        assertEquals(wireMockServer.findAll(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))).size(), 2,
                "validateToken always returning false forces the token to be refetched every time");
    }

    /** {@code onResponse} runs for every response; clearing the token here forces a refetch on the
     * next request even though the token would otherwise still be considered valid. */
    @Test
    public void onResponseHookClearsTokenAfterUse() {
        wireMockServer.stubFor(post(urlEqualTo(TOKEN_ENDPOINT))
                .willReturn(okJson("{\"access_token\":\"" + ACCESS_TOKEN + "\",\"token_type\":\"Bearer\",\"expires_in\":3600}")));
        stubSearchAccounts();

        var authYaml = """
                authentication:
                  rest:
                    oauth2ClientCredentials:
                      onResponse: |
                        if (response.statusCode() == 200) {
                          set("access_token", null)
                        }
                """;

        var connector = initConnectorWithAuth(authYaml);
        searchAccounts(connector);
        searchAccounts(connector);

        assertEquals(wireMockServer.findAll(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))).size(), 2,
                "onResponse cleared the token after the first 200, forcing a refetch on the second request");
    }

    private ClassHandlerConnectorBase initConnectorWithAuth(String authYaml) {
        var connector = YamlOperationsConnector.fromStrings()
                .withGroovyOperations(OPERATION_SCRIPT)
                .withYamlAuthentication(authYaml);
        connector.init(new Config(wireMockServer.port()));
        return connector;
    }

    private static class Config extends BaseTestConfiguration
            implements RestClientConfiguration.OAuth2ClientCredentialsAuthorization {

        private final int port;

        Config(int port) {
            super(port);
            this.port = port;
        }

        @Override public String getRestTestEndpoint() { return null; }
        @Override public String getRestOAuth2TokenUrl() { return "http://localhost:" + port + TOKEN_ENDPOINT; }
        @Override public String getRestOAuth2ClientId() { return "yaml-client"; }
        @Override public GuardedString getRestOAuth2ClientSecret() { return new GuardedString("yaml-secret".toCharArray()); }
        @Override public String getRestOAuth2Scope() { return null; }
        @Override public String getRestOAuth2ClientAuthenticationScheme() { return null; }
    }
}
