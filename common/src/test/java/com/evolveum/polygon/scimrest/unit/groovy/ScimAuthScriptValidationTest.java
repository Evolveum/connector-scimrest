/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.unit.groovy;

import com.evolveum.polygon.scimrest.groovy.impl.ManifestBasedConnector;
import com.evolveum.polygon.scimrest.groovy.impl.ReadOnlyConfiguration;
import com.evolveum.polygon.scimrest.support.WireMockTestSupport;
import org.identityconnectors.framework.common.objects.ScriptContext;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.testng.Assert.assertEquals;

/**
 * The connector development wizard validates the generated authentication script when the user
 * clicks Next, before any credentials exist. A SCIM server that protects {@code /Schemas} and
 * {@code /ResourceTypes} answers 401 at that point, so validating the authentication script must
 * not depend on SCIM discovery. Operation scripts still do, because their object classes come
 * from the discovered schema.
 */
public class ScimAuthScriptValidationTest extends WireMockTestSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String MANIFEST_BASE = "/manifests/script-validation-scim-auth/connector.manifest";
    private static final String SCHEMAS_PATH = "/scim/Schemas";
    private static final String RESOURCE_TYPES_PATH = "/scim/ResourceTypes";

    private static final String GROOVY_AUTH_SCRIPT = """
            authentication {
                rest {
                    basic {
                        implementation {
                            request.header("Authorization", "Basic dXNlcjpwYXNz")
                        }
                    }
                }
            }
            """;

    private static final String YAML_AUTH_SCRIPT = """
            authentication:
              scim:
                basic:
                  implementation: |
                    request.header("Authorization", "Basic dXNlcjpwYXNz")
            """;

    private static class TestConnector extends ManifestBasedConnector {
        TestConnector(int port) {
            super(MANIFEST_BASE);
            var config = new ReadOnlyConfiguration();
            config.setBaseAddress("http://localhost:" + port);
            config.setScimBaseUrl("http://localhost:" + port + "/scim");
            config.setDevelopmentMode(true);
            init(config);
        }
    }

    @BeforeMethod
    public void setUp() {
        setUpWireMock();
        wireMockServer.stubFor(get(urlEqualTo(SCHEMAS_PATH)).willReturn(aResponse().withStatus(401)));
        wireMockServer.stubFor(get(urlEqualTo(RESOURCE_TYPES_PATH)).willReturn(aResponse().withStatus(401)));
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        tearDownWireMock();
    }

    @Test
    public void validatingAuthenticationScriptSucceedsWhenDiscoveryRequiresCredentials() {
        var result = validate(GROOVY_AUTH_SCRIPT, """
                {
                  "operation": "build",
                  "artifactKind": "authentication"
                }
                """);

        assertEquals(result, json("""
                { "status": "ok" }
                """));
    }

    @Test
    public void validatingAuthenticationScriptDoesNotCallScimDiscovery() {
        validate(GROOVY_AUTH_SCRIPT, """
                {
                  "operation": "build",
                  "artifactKind": "authentication"
                }
                """);

        wireMockServer.verify(0, getRequestedFor(urlEqualTo(SCHEMAS_PATH)));
        wireMockServer.verify(0, getRequestedFor(urlEqualTo(RESOURCE_TYPES_PATH)));
    }

    @Test
    public void validatingBrokenAuthenticationScriptStillReportsCompileError() {
        var result = validate("authentication { rest { basic {", """
                {
                  "operation": "build",
                  "artifactKind": "authentication"
                }
                """);

        assertEquals(result.get("status").asString(), "error");
        assertEquals(result.get("errors").get(0).get("phase").asString(), "compile");
    }

    @Test
    public void validatingOperationScriptStillNeedsScimDiscovery() {
        validate("objectClass('User') { search { endpoint('/Users') { } } }", """
                {
                  "operation": "build",
                  "artifactKind": "operation"
                }
                """);

        wireMockServer.verify(getRequestedFor(urlEqualTo(SCHEMAS_PATH)));
    }

    @Test
    public void validatingYamlAuthenticationScriptSucceedsWhenDiscoveryRequiresCredentials() {
        var result = validate("yaml", YAML_AUTH_SCRIPT, """
                {
                  "operation": "build",
                  "artifactKind": "authentication"
                }
                """);

        assertEquals(result, json("""
                { "status": "ok" }
                """));
    }

    @Test
    public void validatingYamlAuthenticationScriptDoesNotCallScimDiscovery() {
        validate("yaml", YAML_AUTH_SCRIPT, """
                {
                  "operation": "build",
                  "artifactKind": "authentication"
                }
                """);

        wireMockServer.verify(0, getRequestedFor(urlEqualTo(SCHEMAS_PATH)));
        wireMockServer.verify(0, getRequestedFor(urlEqualTo(RESOURCE_TYPES_PATH)));
    }

    @Test
    public void validatingBrokenYamlAuthenticationScriptStillReportsCompileError() {
        var result = validate("yaml", "authentication:\n  scim:\n    basic: [", """
                {
                  "operation": "build",
                  "artifactKind": "authentication"
                }
                """);

        assertEquals(result.get("status").asString(), "error");
        assertEquals(result.get("errors").get(0).get("phase").asString(), "compile");
    }

    @Test
    public void validatingYamlOperationScriptStillNeedsScimDiscovery() {
        validate("yaml", "objectClass:\n  User:\n    search:\n      endpoint: /Users\n", """
                {
                  "operation": "build",
                  "artifactKind": "operation"
                }
                """);

        wireMockServer.verify(getRequestedFor(urlEqualTo(SCHEMAS_PATH)));
    }

    private static JsonNode json(String json) {
        return MAPPER.readTree(json);
    }

    private JsonNode validate(String script, String argumentsJson) {
        return validate("groovy", script, argumentsJson);
    }

    @SuppressWarnings("unchecked")
    private JsonNode validate(String language, String script, String argumentsJson) {
        Map<String, Object> arguments = MAPPER.readValue(argumentsJson, Map.class);
        var context = new ScriptContext(language, script, arguments);
        Object result = new TestConnector(wireMockServer.port()).runScriptOnResource(context, null);
        return MAPPER.valueToTree(result);
    }
}
