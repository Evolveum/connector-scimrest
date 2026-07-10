/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.auth.crud;

import com.evolveum.polygon.scimrest.ClassHandlerConnectorBase;
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

        searchAccounts(initConnector());

        assertEquals(wireMockServer.findAll(getRequestedFor(urlEqualTo(ACCOUNTS_PATH))
                .withHeader(CUSTOM_HEADER, equalTo(ACCESS_TOKEN))).size(), 1);
    }

    private ClassHandlerConnectorBase initConnector() {
        var connector = YamlOperationsConnector.fromStrings()
                .withGroovyOperations(OPERATION_SCRIPT)
                .withYamlAuthentication(AUTH_YAML);
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
