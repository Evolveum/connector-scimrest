/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.exception.auth.oauth2clientcredentials;

import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.exception.WireMockTestSupport;
import com.evolveum.polygon.scimrest.groovy.AbstractGroovyRestConnector;
import com.evolveum.polygon.scimrest.groovy.BaseRestGroovyConnectorConfiguration;
import com.evolveum.polygon.scimrest.groovy.GroovyRestHandlerBuilder;
import com.evolveum.polygon.scimrest.groovy.GroovySchemaLoader;
import org.identityconnectors.common.security.GuardedString;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

abstract class AbstractOAuth2ClientCredentialsTests extends WireMockTestSupport {

    protected void stubTokenEndpoint(String url, String accessToken, int expiresIn) {
        var body = "{\"access_token\":\"" + accessToken + "\",\"token_type\":\"Bearer\",\"expires_in\":" + expiresIn + "}";
        wireMockServer.stubFor(post(urlEqualTo(url))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    protected void stubTokenEndpointWithStatus(String url, int status, String accessToken) {
        String body = status == 200
                ? "{\"access_token\":\"" + accessToken + "\",\"token_type\":\"Bearer\",\"expires_in\":3600}"
                : "{\"error\":\"invalid_request\"}";
        wireMockServer.stubFor(post(urlEqualTo(url))
                .willReturn(aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    protected void stubTokenEndpointError(String url, int errorStatus) {
        stubTokenEndpointError(url, errorStatus, null);
    }

    protected void stubTokenEndpointError(String url, int errorStatus, String grantType) {
        var stub = post(urlEqualTo(url));
        if (grantType != null) {
            stub = stub.withRequestBody(containing("grant_type=" + grantType));
        }
        wireMockServer.stubFor(stub.willReturn(aResponse()
                .withStatus(errorStatus)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":\"oauth2_error\"}")));
    }

    protected void stubTokenEndpointMatchingBody(String url, String bodyContains,
                                                 String accessToken, int expiresIn) {
        wireMockServer.stubFor(post(urlEqualTo(url))
                .withRequestBody(containing(bodyContains))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"" + accessToken + "\",\"expires_in\":" + expiresIn + "}")));
    }

    protected void stubRestApiEndpoint(String url, String expectedToken) {
        wireMockServer.stubFor(get(urlEqualTo(url))
                .withHeader("Authorization", equalTo("Bearer " + expectedToken))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"ok\"}")));
    }

    protected static class BaseClientCredentialsConfig extends BaseTestConfiguration
            implements RestClientConfiguration.OAuth2ClientCredentialsAuthorization {

        private final String tokenUrl;
        private final String clientId;
        private final GuardedString clientSecret;

        BaseClientCredentialsConfig(int port, String testEndpoint, String tokenUrl,
                                    String clientId, GuardedString clientSecret) {
            super(port);
            setRestTestEndpoint(testEndpoint);
            this.tokenUrl = tokenUrl;
            this.clientId = clientId;
            this.clientSecret = clientSecret;
        }

        @Override
        public String getRestOAuth2TokenUrl() {
            return tokenUrl;
        }

        @Override
        public String getRestOAuth2ClientId() {
            return clientId;
        }

        @Override
        public GuardedString getRestOAuth2ClientSecret() {
            return clientSecret;
        }

        @Override
        public String getRestOAuth2Scope() {
            return null;
        }

        @Override
        public String getRestOAuth2ClientAuthenticationScheme() {
            return null;
        }
    }

    protected static class OAuth2RestConnector
            extends AbstractGroovyRestConnector<BaseRestGroovyConnectorConfiguration> {

        private final String script;

        OAuth2RestConnector() {
            super(false);
            this.script = null;
        }

        OAuth2RestConnector(String script) {
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
