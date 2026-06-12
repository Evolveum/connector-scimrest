/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.exception.auth.crud;

import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import org.identityconnectors.common.security.GuardedString;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

public class GroovyOAuth2ClientCredentialsCrudTest extends AbstractGroovyAuthCrudTest {

    private static final String TOKEN_ENDPOINT = "/oauth2/token";
    private static final String ACCESS_TOKEN = "groovy-cc-crud-token";
    private static final String CUSTOM_HEADER = "X-Groovy-Token";

    @Override
    protected String authScript() {
        return """
                authentication {
                    rest {
                        oauth2ClientCredentials { oauth2Context ->
                            applyToken { request ->
                                request.header("%s", oauth2Context["access_token"])
                            }
                        }
                    }
                }
                """.formatted(CUSTOM_HEADER);
    }

    @Override
    protected BaseTestConfiguration createConfig(int port) {
        return new Config(port,
                "http://localhost:" + port + TOKEN_ENDPOINT,
                "groovy-client", new GuardedString("groovy-secret".toCharArray()));
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
        return pattern.withHeader(CUSTOM_HEADER, equalTo(ACCESS_TOKEN));
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
