/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.auth.method.oauth2.saml;

import com.evolveum.polygon.scimrest.config.ScimClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.AbstractGroovyRestConnector;
import com.evolveum.polygon.scimrest.groovy.BaseGroovyConnectorConfiguration;
import com.evolveum.polygon.scimrest.groovy.GroovyRestHandlerBuilder;
import com.evolveum.polygon.scimrest.groovy.GroovySchemaLoader;
import org.identityconnectors.common.security.GuardedString;

import java.security.PrivateKey;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

abstract class AbstractScimOAuth2SamlTests extends AbstractOAuth2SamlTests {

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

    protected AbstractGroovyRestConnector<?> createScimConnector(
            PrivateKey privateKey, String tokenEndpoint, String clientId) {
        return createScimConnector(privateKey, tokenEndpoint, clientId, null);
    }

    protected AbstractGroovyRestConnector<?> createScimConnector(
            PrivateKey privateKey, String tokenEndpoint, String clientId, String groovyScript) {
        return createScimConnector(
                new ScimSamlTestConfig(wireMockServer.port(), tokenEndpoint, clientId,
                        new GuardedString(toPem(privateKey).toCharArray())),
                groovyScript);
    }

    protected AbstractGroovyRestConnector<?> createScimConnector(
            ScimSamlTestConfig config, String groovyScript) {
        var connector = new ScimSamlTestConnector(groovyScript);
        connector.init(config);
        return connector;
    }

    protected class ScimSamlTestConfig extends BaseGroovyConnectorConfiguration
            implements ScimClientConfiguration.OAuth2SamlAuthorization {

        private final int port;
        private final String tokenEndpoint;
        private final String clientId;
        private final GuardedString privateKey;
        private String issuer;
        private String scope;
        private String clientAuthenticationScheme;

        ScimSamlTestConfig(int port, String tokenEndpoint, String clientId, GuardedString privateKey) {
            this.port = port;
            this.tokenEndpoint = tokenEndpoint;
            this.clientId = clientId;
            this.privateKey = privateKey;
        }

        @Override public String getScimBaseUrl() { return "http://localhost:" + port + SCIM_BASE_PATH; }
        @Override public String getScimOAuth2TokenUrl() { return "http://localhost:" + port + tokenEndpoint; }
        @Override public String getScimOAuth2ClientId() { return clientId; }
        @Override public GuardedString getScimOAuth2PrivateKey() { return privateKey; }
        @Override public String getScimOAuth2Issuer() { return issuer; }
        @Override public String getScimOAuth2Scope() { return scope; }
        @Override public String getScimOAuth2ClientAuthenticationScheme() { return clientAuthenticationScheme; }

        void setIssuer(String issuer) { this.issuer = issuer; }
        void setScope(String scope) { this.scope = scope; }
        void setClientAuthenticationScheme(String s) { this.clientAuthenticationScheme = s; }
    }

    protected static class ScimSamlTestConnector
            extends AbstractGroovyRestConnector<BaseGroovyConnectorConfiguration> {

        private final String groovyScript;

        ScimSamlTestConnector(String groovyScript) {
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
