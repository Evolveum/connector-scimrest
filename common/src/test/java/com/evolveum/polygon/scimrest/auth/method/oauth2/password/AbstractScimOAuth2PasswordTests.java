/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.auth.method.oauth2.password;

import com.evolveum.polygon.scimrest.config.ScimClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.AbstractGroovyRestConnector;
import com.evolveum.polygon.scimrest.groovy.BaseGroovyConnectorConfiguration;
import com.evolveum.polygon.scimrest.groovy.GroovyRestHandlerBuilder;
import com.evolveum.polygon.scimrest.groovy.GroovySchemaLoader;
import org.identityconnectors.common.security.GuardedString;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

abstract class AbstractScimOAuth2PasswordTests extends AbstractOAuth2PasswordTests {

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
                                                                  String username,
                                                                  GuardedString password) {
        return createScimConnector(tokenEndpoint, clientId, username, password, null);
    }

    protected AbstractGroovyRestConnector<?> createScimConnector(String tokenEndpoint,
                                                                  String clientId,
                                                                  String username,
                                                                  GuardedString password,
                                                                  String groovyScript) {
        var config = new ScimOAuth2PasswordTestConfig(wireMockServer.port(), tokenEndpoint,
                clientId, username, password);
        var connector = new ScimOAuth2PasswordTestConnector(groovyScript);
        connector.init(config);
        return connector;
    }

    protected class ScimOAuth2PasswordTestConfig extends BaseGroovyConnectorConfiguration
            implements ScimClientConfiguration.OAuth2PasswordAuthorization {

        private final int port;
        private final String tokenEndpoint;
        private final String clientId;
        private final String username;
        private final GuardedString password;
        private String scope;
        private String authScheme;

        ScimOAuth2PasswordTestConfig(int port, String tokenEndpoint, String clientId,
                                     String username, GuardedString password) {
            this.port = port;
            this.tokenEndpoint = tokenEndpoint;
            this.clientId = clientId;
            this.username = username;
            this.password = password;
        }

        @Override public String getScimBaseUrl() { return "http://localhost:" + port + SCIM_BASE_PATH; }
        @Override public String getScimOAuth2TokenUrl() { return "http://localhost:" + port + tokenEndpoint; }
        @Override public String getScimOAuth2ClientId() { return clientId; }
        @Override public String getScimOAuth2Username() { return username; }
        @Override public GuardedString getScimOAuth2Password() { return password; }
        @Override public String getScimOAuth2Scope() { return scope; }
        @Override public String getScimOAuth2ClientAuthenticationScheme() { return authScheme; }

        void setScope(String scope) { this.scope = scope; }
        void setAuthScheme(String authScheme) { this.authScheme = authScheme; }
    }

    protected static class ScimOAuth2PasswordTestConnector
            extends AbstractGroovyRestConnector<BaseGroovyConnectorConfiguration> {

        private final String groovyScript;

        ScimOAuth2PasswordTestConnector(String groovyScript) {
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
