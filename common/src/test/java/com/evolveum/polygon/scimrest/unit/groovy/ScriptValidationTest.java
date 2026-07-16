/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.unit.groovy;

import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.AbstractGroovyRestConnector;
import com.evolveum.polygon.scimrest.groovy.BaseGroovyConnectorConfiguration;
import com.evolveum.polygon.scimrest.groovy.GroovyRestHandlerBuilder;
import com.evolveum.polygon.scimrest.groovy.GroovySchemaLoader;

import org.identityconnectors.framework.common.objects.ScriptContext;
import org.testng.annotations.Test;

import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Tests for the script validation via {@link AbstractGroovyRestConnector#runScriptOnResource}.
 */
public class ScriptValidationTest {

    private static final String SCHEMA_SCRIPT =
            "objectClass('User') { attribute('id').connId().type(String.class) }";

    private static final String VALID_OPERATION_SCRIPT =
            "objectClass('User') { search { endpoint('/users') { emptyFilterSupported true } } }";

    private static final String WRONG_DSL_LEVEL_SCRIPT =
            "objectClass('User') { endpoint('/users') { emptyFilterSupported true } }";

    private static final String SYNTAX_ERROR_SCRIPT =
            "objectClass('User') {\n  search {\n";

    public static class TestConfiguration extends BaseGroovyConnectorConfiguration implements RestClientConfiguration {
        @Override public String getBaseAddress()      { return "http://localhost"; }
        @Override public String getRestTestEndpoint() { return null; }
        @Override public Boolean getTrustAllCertificates() { return true; }
    }

    public static class TestConnector extends AbstractGroovyRestConnector<TestConfiguration> {
        private final String schemaScript;
        private final String operationScript;

        TestConnector() {
            this(SCHEMA_SCRIPT, null);
        }

        TestConnector(String schemaScript, String operationScript) {
            this.schemaScript = schemaScript;
            this.operationScript = operationScript;
        }

        @Override
        protected void initializeSchema(GroovySchemaLoader loader) {
            loader.load(schemaScript);
        }

        @Override
        protected void initializeAuthorizationHandler(GroovyRestHandlerBuilder builder) {}

        @Override
        protected void initializeObjectClassHandler(GroovyRestHandlerBuilder builder) {
            if (operationScript != null) {
                builder.loadFromString(operationScript);
            }
        }
    }

    @Test
    public void validOperationScriptPassesValidation() {
        var result = validate(connector(true), VALID_OPERATION_SCRIPT);

        assertEquals(result.get("status"), "ok", "Unexpected result: " + result);
    }

    @Test
    public void syntaxErrorIsReportedAsCompilationErrorWithLine() {
        var result = validate(connector(true), SYNTAX_ERROR_SCRIPT);

        assertEquals(result.get("status"), "error");
        assertEquals(result.get("phase"), "compile");
        assertNotNull(result.get("message"));
        assertNotNull(result.get("line"), "Compilation error should report the line number");
        assertNotNull(result.get("column"), "Compilation error should report the column number");
    }

    @Test
    public void wrongDslLevelIsReportedAsEvaluationError() {
        var result = validate(connector(true), WRONG_DSL_LEVEL_SCRIPT);

        assertEquals(result.get("status"), "error");
        assertEquals(result.get("phase"), "evaluate");
        assertTrue(((String) result.get("message")).contains("endpoint"),
                "Message should mention the failing method: " + result.get("message"));
        assertEquals(result.get("line"), 1, "Evaluation error should report the script line number");
    }

    @Test
    public void brokenDeployedScriptDoesNotBreakValidation() {
        var connector = connector(true, WRONG_DSL_LEVEL_SCRIPT);

        connector.test();
        var result = validate(connector, VALID_OPERATION_SCRIPT);

        assertEquals(result.get("status"), "ok", "Unexpected result: " + result);
    }

    @Test
    public void validationIsRejectedWithoutDevelopmentMode() {
        try {
            validate(connector(false), VALID_OPERATION_SCRIPT);
            fail("Expected UnsupportedOperationException was not thrown");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("development mode"));
        }
    }

    @Test
    public void validSchemaScriptPassesValidation() {
        var result = validate(connector(true), SCHEMA_SCRIPT, "schema");

        assertEquals(result.get("status"), "ok", "Unexpected result: " + result);
    }

    @Test
    public void invalidSchemaDslIsRejectedAsEvaluationError() {
        var result = validate(connector(true),
                "objectClass('User') { nonexistentDslMethod() }", "schema");

        assertEquals(result.get("status"), "error");
        assertEquals(result.get("phase"), "evaluate");
        assertTrue(((String) result.get("message")).contains("nonexistentDslMethod"),
                "Message should mention the failing method: " + result.get("message"));
    }

    @Test
    public void brokenDeployedSchemaScriptIsReportedAsInitializationError() {
        var connector = connector(true, "objectClass('User') { nonexistentDslMethod() }", null);

        var result = validate(connector, VALID_OPERATION_SCRIPT);

        assertEquals(result.get("status"), "error");
        assertEquals(result.get("phase"), "initialization");
        assertTrue(((String) result.get("message")).contains("nonexistentDslMethod"),
                "Message should mention the failing method: " + result.get("message"));
    }

    @Test
    public void validationDoesNotRegisterAnyHandlers() {
        var connector = connector(true);

        validate(connector, VALID_OPERATION_SCRIPT);

        try {
            connector.handlerFor(new org.identityconnectors.framework.common.objects.ObjectClass("User"));
            fail("Validated script must not register any handler");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("Cannot find handler"));
        }
    }

    private static TestConnector connector(boolean developmentMode) {
        return connector(developmentMode, SCHEMA_SCRIPT, null);
    }

    private static TestConnector connector(boolean developmentMode, String operationScript) {
        return connector(developmentMode, SCHEMA_SCRIPT, operationScript);
    }

    private static TestConnector connector(boolean developmentMode, String schemaScript, String operationScript) {
        var configuration = new TestConfiguration();
        configuration.setDevelopmentMode(developmentMode);
        var connector = new TestConnector(schemaScript, operationScript);
        connector.init(configuration);
        return connector;
    }

    private static Map<String, Object> validate(TestConnector connector, String script) {
        return validate(connector, script, "operation");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> validate(TestConnector connector, String script, String artifactKind) {
        ScriptContext context = new ScriptContext("groovy", script, Map.of(
                AbstractGroovyRestConnector.SCRIPT_ARGUMENT_OPERATION,
                AbstractGroovyRestConnector.SCRIPT_OPERATION_VALIDATE,
                AbstractGroovyRestConnector.SCRIPT_ARGUMENT_ARTIFACT_KIND, artifactKind));
        return (Map<String, Object>) connector.runScriptOnResource(context, null);
    }
}
