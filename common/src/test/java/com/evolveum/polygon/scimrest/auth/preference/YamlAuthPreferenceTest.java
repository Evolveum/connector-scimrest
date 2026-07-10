/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.auth.preference;

import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.GroovyRestHandlerBuilder;
import com.evolveum.polygon.scimrest.groovy.GroovySchemaLoader;
import com.evolveum.polygon.scimrest.groovy.impl.ManifestBasedConnector;
import com.evolveum.polygon.scimrest.support.WireMockTestSupport;
import com.evolveum.polygon.scimrest.yaml.YamlRestHandlerLoader;
import org.identityconnectors.common.security.GuardedString;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.assertEquals;

/**
 * YAML counterpart of {@code AuthPreferenceTests}: a {@code preference} list drives probe-based auth
 * method selection during {@code test()}. Verifies the YAML preference directive.
 */
public class YamlAuthPreferenceTest extends WireMockTestSupport {

    private static final String TEST_ENDPOINT = "/test";

    private static final String YAML = """
            authentication:
              rest:
                apiKey:
                  implementation: |
                    request.header("X-Api-Key", "primary-key")
                bearer:
                  implementation: |
                    request.header("Authorization", "Bearer fallback-token")
                preference:
                  - apiKey
                  - bearer
            """;

    @BeforeMethod
    public void setUp() {
        setUpWireMock();
    }

    @AfterMethod
    public void tearDown() {
        tearDownWireMock();
    }

    @Test
    public void primaryMethodSelectedByProbe() {
        wireMockServer.stubFor(get(urlEqualTo(TEST_ENDPOINT))
                .withHeader("X-Api-Key", equalTo("primary-key"))
                .willReturn(aResponse().withStatus(200)));

        var config = new Config(wireMockServer.port());
        config.testEndpoint = TEST_ENDPOINT;
        var connector = new YamlAuthConnector(YAML);
        connector.init(config);
        connector.test();

        assertEquals(wireMockServer.findAll(getRequestedFor(urlEqualTo(TEST_ENDPOINT))
                .withHeader("X-Api-Key", equalTo("primary-key"))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 1);
    }

    @Test
    public void fallbackMethodSelectedWhenPrimaryFails() {
        wireMockServer.stubFor(get(urlEqualTo(TEST_ENDPOINT))
                .withHeader("X-Api-Key", equalTo("primary-key"))
                .willReturn(aResponse().withStatus(401)));
        wireMockServer.stubFor(get(urlEqualTo(TEST_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer fallback-token"))
                .willReturn(aResponse().withStatus(200)));

        var config = new Config(wireMockServer.port());
        config.testEndpoint = TEST_ENDPOINT;
        var connector = new YamlAuthConnector(YAML);
        connector.init(config);
        connector.test();

        assertEquals(wireMockServer.findAll(getRequestedFor(urlEqualTo(TEST_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer fallback-token"))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 2);
    }

    private static class YamlAuthConnector extends ManifestBasedConnector {

        private final String yaml;

        YamlAuthConnector(String yaml) {
            this.yaml = yaml;
        }

        @Override protected void initializeSchema(GroovySchemaLoader loader) { }

        @Override protected void initializeAuthorizationHandler(GroovyRestHandlerBuilder builder) {
            new YamlRestHandlerLoader(builder, getConfiguration().groovyContext()).loadFromString(yaml);
        }

        @Override protected void initializeObjectClassHandler(GroovyRestHandlerBuilder builder) { }
    }

    private static class Config extends BaseTestConfiguration
            implements RestClientConfiguration.ApiKeyAuthorization,
            RestClientConfiguration.BearerTokenAuthorization {

        String testEndpoint;

        Config(int port) {
            super(port);
        }

        @Override public String getRestTestEndpoint() { return testEndpoint; }
        @Override public GuardedString getRestApiKey() { return new GuardedString("primary-key".toCharArray()); }
        @Override public String getRestApiKeyName() { return "X-Api-Key"; }
        @Override public String getRestApiKeyLocation() { return "header"; }
        @Override public GuardedString getRestTokenValue() { return new GuardedString("fallback-token".toCharArray()); }
    }
}
