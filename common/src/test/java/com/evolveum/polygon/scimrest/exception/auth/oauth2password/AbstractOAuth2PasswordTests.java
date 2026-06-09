/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.exception.auth.oauth2password;

import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.exception.WireMockTestSupport;
import com.evolveum.polygon.scimrest.groovy.AbstractGroovyRestConnector;
import com.evolveum.polygon.scimrest.groovy.BaseRestGroovyConnectorConfiguration;
import com.evolveum.polygon.scimrest.groovy.GroovyRestHandlerBuilder;
import com.evolveum.polygon.scimrest.groovy.GroovySchemaLoader;
import org.identityconnectors.common.security.GuardedString;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

abstract class AbstractOAuth2PasswordTests extends WireMockTestSupport {

    protected void stubTokenEndpoint(String url, String accessToken, int expiresIn) {
        var body = "{\"access_token\":\"" + accessToken + "\",\"token_type\":\"Bearer\",\"expires_in\":" + expiresIn + "}";
        wireMockServer.stubFor(post(urlEqualTo(url))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    protected void stubTokenEndpointMatchingBody(String url, String bodyContains,
                                                 String accessToken, int expiresIn) {
        wireMockServer.stubFor(post(urlEqualTo(url))
                .withRequestBody(containing(bodyContains))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"" + accessToken + "\",\"token_type\":\"Bearer\",\"expires_in\":" + expiresIn + "}")));
    }

    protected void stubTokenEndpointError(String url, int errorStatus) {
        wireMockServer.stubFor(post(urlEqualTo(url))
                .willReturn(aResponse()
                        .withStatus(errorStatus)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"oauth2_error\"}")));
    }

    protected void stubRestApiEndpoint(String url, String expectedToken) {
        wireMockServer.stubFor(get(urlEqualTo(url))
                .withHeader("Authorization", equalTo("Bearer " + expectedToken))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"ok\"}")));
    }

    protected static class BasePasswordConfig extends BaseTestConfiguration
            implements RestClientConfiguration.OAuth2PasswordAuthorization {

        private final String tokenUrl;
        private final String clientId;
        private final String username;
        private final GuardedString password;
        private String scope;
        private String authScheme;

        BasePasswordConfig(int port, String testEndpoint, String tokenUrl,
                           String clientId, String username, GuardedString password) {
            super(port);
            setRestTestEndpoint(testEndpoint);
            this.tokenUrl = tokenUrl;
            this.clientId = clientId;
            this.username = username;
            this.password = password;
        }

        @Override public String getRestOAuth2TokenUrl() { return tokenUrl; }
        @Override public String getRestOAuth2ClientId() { return clientId; }
        @Override public String getRestOAuth2Username() { return username; }
        @Override public GuardedString getRestOAuth2Password() { return password; }
        @Override public String getRestOAuth2Scope() { return scope; }
        @Override public String getRestOAuth2ClientAuthenticationScheme() { return authScheme; }

        void setScope(String scope) { this.scope = scope; }
        void setAuthScheme(String authScheme) { this.authScheme = authScheme; }
    }

    protected static class OAuth2PasswordRestConnector
            extends AbstractGroovyRestConnector<BaseRestGroovyConnectorConfiguration> {

        private final String script;

        OAuth2PasswordRestConnector() {
            super(false);
            this.script = null;
        }

        OAuth2PasswordRestConnector(String script) {
            super(false);
            this.script = script;
        }

        @Override
        protected void initializeSchema(GroovySchemaLoader loader) {
        }

        @Override
        protected void initializeAuthorizationHandler(GroovyRestHandlerBuilder builder) {
            if (script != null) builder.loadFromString(script);
        }

        @Override
        protected void initializeObjectClassHandler(GroovyRestHandlerBuilder builder) {
        }
    }
}
