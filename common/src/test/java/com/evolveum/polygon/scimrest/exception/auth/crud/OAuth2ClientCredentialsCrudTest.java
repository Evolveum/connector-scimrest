/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.exception.auth.crud;

import com.evolveum.polygon.scimrest.ClassHandlerConnectorBase;
import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import org.identityconnectors.common.security.GuardedString;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

public class OAuth2ClientCredentialsCrudTest extends AbstractAuthOnCrudTest {

    private static final String TOKEN_ENDPOINT = "/oauth2/token";
    private static final String ACCESS_TOKEN = "oauth2-cc-crud-token";

    @Override
    protected ClassHandlerConnectorBase createConnector() {
        var config = new Config(wireMockServer.port(),
                "http://localhost:" + wireMockServer.port() + TOKEN_ENDPOINT,
                "crud-client", new GuardedString("crud-secret".toCharArray()));
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
            implements RestClientConfiguration.OAuth2ClientCredentialsAuthorization {

        private final String tokenUrl;
        private final String clientId;
        private final GuardedString clientSecret;

        Config(int port, String tokenUrl, String clientId, GuardedString clientSecret) {
            super(port);
            this.tokenUrl = tokenUrl;
            this.clientId = clientId;
            this.clientSecret = clientSecret;
        }

        @Override public String getRestOAuth2TokenUrl() { return tokenUrl; }
        @Override public String getRestOAuth2ClientId() { return clientId; }
        @Override public GuardedString getRestOAuth2ClientSecret() { return clientSecret; }
        @Override public String getRestOAuth2Scope() { return null; }
        @Override public String getRestOAuth2ClientAuthenticationScheme() { return null; }
    }

}
