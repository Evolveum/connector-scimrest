/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.auth.method.basic;

import com.evolveum.polygon.scimrest.config.ScimClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.BaseGroovyConnectorConfiguration;
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
 * YAML counterpart of {@code ScimHttpBasicTests}: a {@code authentication.scim.basic.implementation}
 * block sets the auth header on the SCIM discovery requests. Verifies the YAML scim auth channel.
 */
public class YamlScimBasicAuthTest extends WireMockTestSupport {

    private static final String SCIM_BASE_PATH = "/scim";
    private static final String SCHEMAS_ENDPOINT = SCIM_BASE_PATH + "/Schemas";
    private static final String RESOURCES_ENDPOINT = SCIM_BASE_PATH + "/ResourceTypes";
    private static final String AUTH_HEADER = "Basic dXNlcjpwYXNz";

    private static final String EMPTY_LIST_RESPONSE = """
            {"schemas":["urn:ietf:params:scim:api:messages:2.0:ListResponse"],"totalResults":0,"Resources":[]}
            """;

    private static final String YAML = """
            authentication:
              scim:
                basic:
                  implementation: |
                    request.header("Authorization", "Basic dXNlcjpwYXNz")
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
    public void scimBasicAuthFromYaml() {
        stubScimEndpoints();

        var connector = new YamlAuthConnector(YAML);
        connector.init(new ScimConfig(wireMockServer.port()));
        connector.schema();

        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(SCHEMAS_ENDPOINT))
                .withHeader("Authorization", equalTo(AUTH_HEADER))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 2);
    }

    private void stubScimEndpoints() {
        for (String endpoint : new String[]{SCHEMAS_ENDPOINT, RESOURCES_ENDPOINT}) {
            wireMockServer.stubFor(get(urlPathEqualTo(endpoint))
                    .withHeader("Authorization", equalTo(AUTH_HEADER))
                    .willReturn(aResponse().withStatus(200)
                            .withHeader("Content-Type", "application/scim+json")
                            .withBody(EMPTY_LIST_RESPONSE)));
        }
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

    private static class ScimConfig extends BaseGroovyConnectorConfiguration
            implements ScimClientConfiguration.BasicAuthorization {

        private final int port;

        ScimConfig(int port) {
            this.port = port;
        }

        @Override public String getScimBaseUrl() {
            return "http://localhost:" + port + SCIM_BASE_PATH;
        }

        @Override public String getScimUsername() {
            return "user";
        }

        @Override public GuardedString getScimPassword() {
            return new GuardedString("pass".toCharArray());
        }
    }
}
