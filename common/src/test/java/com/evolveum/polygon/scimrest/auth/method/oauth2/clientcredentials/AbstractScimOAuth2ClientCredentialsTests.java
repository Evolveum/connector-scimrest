/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.auth.method.oauth2.clientcredentials;

import com.evolveum.polygon.scimrest.config.ScimClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.AbstractGroovyRestConnector;
import com.evolveum.polygon.scimrest.groovy.BaseGroovyConnectorConfiguration;
import com.evolveum.polygon.scimrest.groovy.GroovyRestHandlerBuilder;
import com.evolveum.polygon.scimrest.groovy.GroovySchemaLoader;
import org.identityconnectors.common.security.GuardedString;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

abstract class AbstractScimOAuth2ClientCredentialsTests extends AbstractOAuth2ClientCredentialsTests {

    protected static final String SCIM_BASE_PATH = "/scim";
    protected static final String SCHEMAS_ENDPOINT = SCIM_BASE_PATH + "/Schemas";
    protected static final String RESOURCES_ENDPOINT = SCIM_BASE_PATH + "/ResourceTypes";

    protected static final String EMPTY_LIST_RESPONSE = """
            {
              "schemas": ["urn:ietf:params:scim:api:messages:2.0:ListResponse"],
              "totalResults": 0,
              "Resources": []
            }
            """;

    protected void stubScimEndpoints(String expectedToken) {
        wireMockServer.stubFor(get(urlPathEqualTo(SCHEMAS_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer " + expectedToken))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/scim+json")
                        .withBody(EMPTY_LIST_RESPONSE)));
        wireMockServer.stubFor(get(urlPathEqualTo(RESOURCES_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer " + expectedToken))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/scim+json")
                        .withBody(EMPTY_LIST_RESPONSE)));
    }

    protected AbstractGroovyRestConnector<?> createScimConnector(String tokenEndpoint,
                                                                 String clientId,
                                                                 GuardedString clientSecret) {
        return createScimConnector(tokenEndpoint, clientId, clientSecret, null);
    }

    protected AbstractGroovyRestConnector<?> createScimConnector(String tokenEndpoint,
                                                                 String clientId,
                                                                 GuardedString clientSecret,
                                                                 String groovyScript) {
        var config = new ScimOAuth2TestConfig(wireMockServer.port(), tokenEndpoint, clientId, clientSecret);
        var connector = new ScimOAuth2TestConnector(groovyScript);
        connector.init(config);
        return connector;
    }

    protected class ScimOAuth2TestConfig extends BaseGroovyConnectorConfiguration
            implements ScimClientConfiguration.OAuth2ClientCredentialsAuthorization {

        private final int port;
        private final String tokenEndpoint;
        private final String clientId;
        private final GuardedString clientSecret;

        ScimOAuth2TestConfig(int port, String tokenEndpoint, String clientId, GuardedString clientSecret) {
            this.port = port;
            this.tokenEndpoint = tokenEndpoint;
            this.clientId = clientId;
            this.clientSecret = clientSecret;
        }

        @Override
        public String getScimBaseUrl() {
            return "http://localhost:" + port + SCIM_BASE_PATH;
        }

        @Override
        public String getScimOAuth2TokenUrl() {
            return "http://localhost:" + port + tokenEndpoint;
        }

        @Override
        public String getScimOAuth2ClientId() {
            return clientId;
        }

        @Override
        public GuardedString getScimOAuth2ClientSecret() {
            return clientSecret;
        }

        @Override
        public String getScimOAuth2Scope() {
            return null;
        }

        @Override
        public String getScimOAuth2ClientAuthenticationScheme() {
            return null;
        }
    }

    protected static class ScimOAuth2TestConnector
            extends AbstractGroovyRestConnector<BaseGroovyConnectorConfiguration> {

        private final String groovyScript;

        ScimOAuth2TestConnector(String groovyScript) {
            super(false);
            this.groovyScript = groovyScript;
        }

        @Override
        protected void initializeSchema(GroovySchemaLoader loader) {
        }

        @Override
        protected void initializeAuthorizationHandler(GroovyRestHandlerBuilder builder) {
            if (groovyScript != null) builder.loadFromString(groovyScript);
        }

        @Override
        protected void initializeObjectClassHandler(GroovyRestHandlerBuilder builder) {
        }
    }
}
