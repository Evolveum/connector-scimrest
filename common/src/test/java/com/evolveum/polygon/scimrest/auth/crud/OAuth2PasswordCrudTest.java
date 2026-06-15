/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.auth.crud;

import com.evolveum.polygon.scimrest.ClassHandlerConnectorBase;
import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import org.identityconnectors.common.security.GuardedString;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

public class OAuth2PasswordCrudTest extends AbstractAuthOnCrudTest {

    private static final String TOKEN_ENDPOINT = "/oauth2/token";
    private static final String ACCESS_TOKEN = "oauth2-pwd-crud-token";

    @Override
    protected ClassHandlerConnectorBase createConnector() {
        var config = new Config(wireMockServer.port(),
                "http://localhost:" + wireMockServer.port() + TOKEN_ENDPOINT,
                "crud-client", "crud-user", new GuardedString("crud-password".toCharArray()));
        var connector = new TestConnector();
        connector.init(config);
        return connector;
    }

    @Override
    protected void stubAuthPrerequisites() {
        wireMockServer.stubFor(post(urlEqualTo(TOKEN_ENDPOINT))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"" + ACCESS_TOKEN + "\",\"token_type\":\"Bearer\",\"expires_in\":3600}")));
    }

    @Override
    protected RequestPatternBuilder withExpectedAuth(RequestPatternBuilder pattern) {
        return pattern.withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN));
    }

    private static class Config extends BaseTestConfiguration
            implements RestClientConfiguration.OAuth2PasswordAuthorization {

        private final String tokenUrl;
        private final String clientId;
        private final String username;
        private final GuardedString password;

        Config(int port, String tokenUrl, String clientId, String username, GuardedString password) {
            super(port);
            this.tokenUrl = tokenUrl;
            this.clientId = clientId;
            this.username = username;
            this.password = password;
        }

        @Override public String getRestOAuth2TokenUrl() { return tokenUrl; }
        @Override public String getRestOAuth2ClientId() { return clientId; }
        @Override public String getRestOAuth2Username() { return username; }
        @Override public GuardedString getRestOAuth2Password() { return password; }
        @Override public String getRestOAuth2Scope() { return null; }
        @Override public String getRestOAuth2ClientAuthenticationScheme() { return null; }
    }

}
