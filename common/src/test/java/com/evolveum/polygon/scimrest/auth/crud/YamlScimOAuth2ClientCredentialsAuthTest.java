/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.auth.crud;

import com.evolveum.polygon.conndev.groovy.BaseGroovyConnectorConfiguration;
import com.evolveum.polygon.conndev.groovy.GroovySchemaLoader;
import com.evolveum.polygon.scimrest.config.ScimClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.handler.GroovyRestHandlerBuilder;
import com.evolveum.polygon.conndev.groovy.GroovyScriptLoader;
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
 * YAML counterpart of the {@code rest} channel's {@code oauth2ClientCredentials} coverage, but on the
 * {@code scim:} channel — {@link com.evolveum.polygon.scimrest.yaml.binding.ScimAuthChannelHandler} has
 * its own, separate {@code switch} dispatch from {@link com.evolveum.polygon.scimrest.yaml.binding.RestAuthChannelHandler},
 * so a typo there would not be caught by testing the {@code rest} channel alone.
 */
public class YamlScimOAuth2ClientCredentialsAuthTest extends WireMockTestSupport {

    private static final String SCIM_BASE_PATH = "/scim";
    private static final String SCHEMAS_ENDPOINT = SCIM_BASE_PATH + "/Schemas";
    private static final String RESOURCES_ENDPOINT = SCIM_BASE_PATH + "/ResourceTypes";
    private static final String TOKEN_ENDPOINT = "/oauth2/token";
    private static final String ACCESS_TOKEN = "yaml-scim-cc-token";

    private static final String EMPTY_LIST_RESPONSE = """
            {"schemas":["urn:ietf:params:scim:api:messages:2.0:ListResponse"],"totalResults":0,"Resources":[]}
            """;

    private static final String YAML = """
            authentication:
              scim:
                oauth2ClientCredentials:
                  applyToken: |
                    request.header("Authorization", "Bearer " + get("access_token"))
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
    public void scimOauth2ClientCredentialsFromYaml() {
        wireMockServer.stubFor(post(urlEqualTo(TOKEN_ENDPOINT))
                .willReturn(okJson("{\"access_token\":\"" + ACCESS_TOKEN + "\",\"token_type\":\"Bearer\",\"expires_in\":3600}")));
        stubScimEndpoints();

        var connector = new YamlAuthConnector(YAML);
        connector.init(new ScimConfig(wireMockServer.port()));
        connector.schema();

        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(SCHEMAS_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN))).size(), 1);
    }

    private void stubScimEndpoints() {
        for (String endpoint : new String[]{SCHEMAS_ENDPOINT, RESOURCES_ENDPOINT}) {
            wireMockServer.stubFor(get(urlPathEqualTo(endpoint))
                    .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN))
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

        @Override protected void initializeObjectClassHandler(GroovyScriptLoader builder) { }
    }

    private static class ScimConfig extends BaseGroovyConnectorConfiguration
            implements ScimClientConfiguration.OAuth2ClientCredentialsAuthorization {

        private final int port;

        ScimConfig(int port) {
            this.port = port;
        }

        @Override public String getScimBaseUrl() {
            return "http://localhost:" + port + SCIM_BASE_PATH;
        }

        @Override public String getScimOAuth2TokenUrl() {
            return "http://localhost:" + port + TOKEN_ENDPOINT;
        }

        @Override public String getScimOAuth2ClientId() { return "yaml-scim-client"; }
        @Override public GuardedString getScimOAuth2ClientSecret() { return new GuardedString("yaml-scim-secret".toCharArray()); }
        @Override public String getScimOAuth2Scope() { return null; }
        @Override public String getScimOAuth2ClientAuthenticationScheme() { return null; }
    }
}
