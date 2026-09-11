/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.auth.crud;

import com.evolveum.polygon.conndev.spi.ClassHandlerConnectorBase;
import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.support.AbstractCrudConnectorTest;
import com.evolveum.polygon.scimrest.support.YamlOperationsConnector;
import org.identityconnectors.common.security.GuardedString;
import org.testng.annotations.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.assertEquals;

/**
 * YAML {@code authentication.rest.awsSignature.beforeSign} block reaching a CRUD operation.
 *
 * <p>Regression test for the {@code awsSignature} auth method being unreachable from YAML: the
 * {@code rest}/{@code scim} channel dispatch had no {@code case "awsSignature"}, so this key would
 * fail with "Unknown auth method 'awsSignature'" even though the builder API fully supported it
 * (and the equivalent Groovy DSL, {@link GroovyAwsSignatureCrudTest}, already worked).
 */
public class YamlAwsSignatureCrudTest extends AbstractCrudConnectorTest {

    private static final String YAML_HEADER = "X-Yaml-Aws";
    private static final String YAML_HEADER_VALUE = "yaml-hook";

    private static final String AUTH_YAML = """
            authentication:
              rest:
                awsSignature:
                  beforeSign: |
                    request.header("%s", "%s")
            """.formatted(YAML_HEADER, YAML_HEADER_VALUE);

    @Test
    public void awsSignatureAppliedToSearchFromYaml() {
        stubSearchAccounts();

        searchAccounts(initConnectorWithYamlAuth());

        assertEquals(wireMockServer.findAll(
                getRequestedFor(urlEqualTo(ACCOUNTS_PATH))
                        .withHeader("Authorization", matching("AWS4-HMAC-SHA256.*"))
                        .withHeader(YAML_HEADER, equalTo(YAML_HEADER_VALUE))).size(), 1);
    }

    private ClassHandlerConnectorBase initConnectorWithYamlAuth() {
        var connector = YamlOperationsConnector.fromStrings()
                .withGroovyOperations(OPERATION_SCRIPT)
                .withYamlAuthentication(AUTH_YAML);
        connector.init(new Config(wireMockServer.port()));
        return connector;
    }

    /** Same config contract as {@link GroovyAwsSignatureCrudTest}. */
    private static class Config extends BaseTestConfiguration
            implements RestClientConfiguration.AwsSignatureAuthorization {

        Config(int port) { super(port); }

        @Override public String getRestTestEndpoint() { return null; }
        @Override public String getRestAwsAccessKey() { return "AKIATEST"; }
        @Override public GuardedString getRestAwsSecretKey() { return new GuardedString("test-secret-key".toCharArray()); }
        @Override public String getRestAwsRegion() { return "us-east-1"; }
        @Override public String getRestAwsService() { return "execute-api"; }
        @Override public GuardedString getRestAwsSessionToken() { return null; }
    }
}
