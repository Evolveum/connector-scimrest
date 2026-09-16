/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.unit.groovy;

import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.connector.AbstractGroovyRestConnector;
import com.evolveum.polygon.conndev.groovy.BaseGroovyConnectorConfiguration;
import com.evolveum.polygon.conndev.groovy.ScriptValidationRequest;
import com.evolveum.polygon.scimrest.groovy.handler.GroovyRestHandlerBuilder;
import org.identityconnectors.framework.common.objects.ObjectClass;
import com.evolveum.polygon.conndev.groovy.GroovySchemaLoader;

import org.identityconnectors.framework.common.objects.ScriptContext;
import org.testng.annotations.Test;

import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * YAML counterpart of {@link ScriptValidationTest} — the language dispatch, and that the
 * compile-phase syntax check actually fires for embedded fragments, including inside a {@code
 * supportedFilters} block.
 */
public class YamlScriptValidationTest {

    private static final String SCHEMA_SCRIPT = """
            objectClasses:
              User:
                attributes:
                  id:
            """;

    private static final String VALID_OPERATION_SCRIPT = """
            objectClasses:
              User:
                search:
                  endpoints:
                    - path: users
                      emptyFilterSupported: true
            """;

    private static final String OPERATION_SCRIPT_WITH_BROKEN_EXTRACTOR = """
            objectClasses:
              User:
                search:
                  endpoints:
                    - path: users
                      emptyFilterSupported: true
                      objectExtractor: |
                        return (
            """;

    private static final String OPERATION_SCRIPT_WITH_BROKEN_SUPPORTED_FILTER_SPEC = """
            objectClasses:
              User:
                search:
                  endpoints:
                    - path: users
                      supportedFilters:
                        - spec: |
                            return (
                          request: |
                            request.pathParameter("id", value)
            """;

    public static class TestConfiguration extends BaseGroovyConnectorConfiguration implements RestClientConfiguration {
        @Override public String getBaseAddress()      { return "http://localhost"; }
        @Override public String getRestTestEndpoint() { return null; }
        @Override public Boolean getTrustAllCertificates() { return true; }
    }

    public static class TestConnector extends AbstractGroovyRestConnector<TestConfiguration> {
        @Override
        protected void initializeSchema(GroovySchemaLoader loader) {
            loader.load("objectClass('User') { attribute('id').connId().type(String.class) }");
        }

        @Override
        protected void initializeAuthorizationHandler(GroovyRestHandlerBuilder builder) {}

        @Override
        protected void initializeObjectClassHandler(GroovyRestHandlerBuilder builder) {}
    }

    @Test
    public void validYamlSchemaScriptPassesValidation() {
        var result = validate(SCHEMA_SCRIPT, "schema", ScriptValidationRequest.SCRIPT_OPERATION_BUILD);

        assertEquals(result.get("status"), "ok", "Unexpected result: " + result);
    }

    @Test
    public void validYamlOperationScriptPassesValidation() {
        var result = validate(VALID_OPERATION_SCRIPT, "operation", ScriptValidationRequest.SCRIPT_OPERATION_BUILD);

        assertEquals(result.get("status"), "ok", "Unexpected result: " + result);
    }

    @Test
    public void yamlOperationCompileNeverRegistersAnyHandler() {
        var connector = connector();

        var result = validate(connector, VALID_OPERATION_SCRIPT, "operation", ScriptValidationRequest.SCRIPT_OPERATION_COMPILE);

        assertEquals(result.get("status"), "ok", "Unexpected result: " + result);
        try {
            connector.handlerFor(new ObjectClass("User"));
            fail("Validated script must not register any handler");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("Cannot find handler"));
        }
    }

    @Test
    public void embeddedGroovySyntaxErrorIsCaughtAtCompileNotJustBuild() {
        var result = validate(
                OPERATION_SCRIPT_WITH_BROKEN_EXTRACTOR, "operation", ScriptValidationRequest.SCRIPT_OPERATION_COMPILE);

        assertFirstErrorPhaseAndSource(result, "compile", "objectClasses.User.search.endpoints[0].objectExtractor");
    }

    @Test
    public void supportedFilterSpecSyntaxErrorIsCaughtAtCompile() {
        var result = validate(
                OPERATION_SCRIPT_WITH_BROKEN_SUPPORTED_FILTER_SPEC, "operation", ScriptValidationRequest.SCRIPT_OPERATION_COMPILE);

        assertFirstErrorPhaseAndSource(result, "compile",
                "objectClasses.User.search.endpoints[0].supportedFilters[0].spec");
    }

    @SuppressWarnings("unchecked")
    private static void assertFirstErrorPhaseAndSource(Map<String, Object> result, String phase, String source) {
        assertEquals(result.get("status"), "error", "Unexpected result: " + result);
        var errors = (java.util.List<Map<String, Object>>) result.get("errors");
        assertEquals(errors.get(0).get("phase"), phase, "Unexpected result: " + result);
        assertEquals(errors.get(0).get("source"), source, "Unexpected result: " + result);
    }

    private static TestConnector connector() {
        var configuration = new TestConfiguration();
        configuration.setDevelopmentMode(true);
        var connector = new TestConnector();
        connector.init(configuration);
        return connector;
    }

    private static Map<String, Object> validate(String script, String artifactKind, String operation) {
        return validate(connector(), script, artifactKind, operation);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> validate(TestConnector connector, String script, String artifactKind, String operation) {
        ScriptContext context = new ScriptContext("yaml", script, Map.of(
                ScriptValidationRequest.SCRIPT_ARGUMENT_OPERATION, operation,
                ScriptValidationRequest.SCRIPT_ARGUMENT_ARTIFACT_KIND, artifactKind));
        return (Map<String, Object>) connector.runScriptOnResource(context, null);
    }
}
