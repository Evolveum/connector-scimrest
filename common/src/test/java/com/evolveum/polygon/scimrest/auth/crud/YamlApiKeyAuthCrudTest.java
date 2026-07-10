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
 * YAML {@code authentication.rest.apiKey.implementation} block reaching a CRUD operation: schema and
 * operations stay on Groovy, only the authorization handler comes from YAML.
 */
public class YamlApiKeyAuthCrudTest extends AbstractCrudConnectorTest {

    private static final String API_KEY = "groovy-crud-api-key";
    private static final String CUSTOM_HEADER = "X-Groovy-Api-Key";

    /** Declarative structure; the imperative body rides along as a block scalar. */
    private static final String AUTH_YAML = """
            authentication:
              rest:
                apiKey:
                  implementation: |
                    request.header("X-Groovy-Api-Key", decrypt(configuration.getRestApiKey()))
            """;

    @Test
    public void apiKeyHeaderAppliedToSearchFromYaml() {
        stubSearchAccounts();

        searchAccounts(initConnectorWithYamlAuth());

        assertEquals(wireMockServer.findAll(
                getRequestedFor(urlEqualTo(ACCOUNTS_PATH))
                        .withHeader(CUSTOM_HEADER, equalTo(API_KEY))).size(), 1);
    }

    private ClassHandlerConnectorBase initConnectorWithYamlAuth() {
        var connector = YamlOperationsConnector.fromStrings()
                .withGroovyOperations(OPERATION_SCRIPT)
                .withYamlAuthentication(AUTH_YAML);
        connector.init(new Config(wireMockServer.port()));
        return connector;
    }

    /** Same config contract as {@link GroovyApiKeyCrudTest}, exposing the API key to decrypt. */
    private static class Config extends BaseTestConfiguration
            implements RestClientConfiguration.ApiKeyAuthorization {

        Config(int port) { super(port); }

        @Override public String getRestTestEndpoint() { return null; }
        @Override public GuardedString getRestApiKey() { return new GuardedString(API_KEY.toCharArray()); }
        @Override public String getRestApiKeyName() { return "X-Api-Key"; }
        @Override public String getRestApiKeyLocation() { return "header"; }
    }
}
