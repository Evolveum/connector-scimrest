/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.exception.auth.oauth2jwtbearer;

import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.exception.WireMockTestSupport;
import com.evolveum.polygon.scimrest.groovy.AbstractGroovyRestConnector;
import com.evolveum.polygon.scimrest.groovy.BaseRestGroovyConnectorConfiguration;
import com.evolveum.polygon.scimrest.groovy.GroovyRestHandlerBuilder;
import com.evolveum.polygon.scimrest.groovy.GroovySchemaLoader;
import org.identityconnectors.common.security.GuardedString;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.util.Base64;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

abstract class AbstractOAuth2JwtBearerTests extends WireMockTestSupport {

    protected void stubTokenEndpointWithStatus(String url, int status, String accessToken) {
        String body = status == 200
                ? "{\"access_token\":\"" + accessToken + "\",\"expires_in\":3600}"
                : "{\"error\":\"invalid_request\"}";
        wireMockServer.stubFor(post(urlEqualTo(url))
                .willReturn(aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    protected void stubRestApiEndpoint(String url, String expectedToken) {
        wireMockServer.stubFor(get(urlEqualTo(url))
                .withHeader("Authorization", equalTo("Bearer " + expectedToken))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"ok\"}")));
    }

    protected static class BaseJwtBearerConfig extends BaseTestConfiguration
            implements RestClientConfiguration.OAuth2JwtBearerAuthorization {

        private final String tokenUrl;
        private final String clientId;
        private final GuardedString privateKey;

        BaseJwtBearerConfig(int port, String testEndpoint, String tokenUrl,
                            String clientId, GuardedString privateKey) {
            super(port);
            setRestTestEndpoint(testEndpoint);
            this.tokenUrl = tokenUrl;
            this.clientId = clientId;
            this.privateKey = privateKey;
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
        public GuardedString getRestOAuth2PrivateKey() {
            return privateKey;
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

    protected static String toPem(PrivateKey key) {
        String encoded = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                .encodeToString(key.getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + encoded + "\n-----END PRIVATE KEY-----\n";
    }
}
