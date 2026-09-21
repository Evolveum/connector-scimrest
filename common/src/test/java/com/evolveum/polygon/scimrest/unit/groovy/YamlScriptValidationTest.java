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

import java.util.List;
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

    private static final String OPERATION_SCRIPT_WITH_UNKNOWN_FILTER_ATTRIBUTE = """
            objectClasses:
              User:
                search:
                  endpoints:
                    - path: users
                      supportedFilters:
                        - spec: |
                            attribute("missing").eq().anySingleValue()
                          request: |
                            request.pathParameter("id", value)
            """;

    private static final String VALID_SEARCH_BY_ID_SCRIPT = """
            objectClasses:
              User:
                search:
                  endpoints:
                    - path: users/{id}
                      singleResult: true
                      supportedFilters:
                        - spec: |
                            attribute("id").eq().anySingleValue()
                          request: |
                            request.pathParameter("id", value)
            """;

    private static final String VALID_SEARCH_FILTER_SCRIPT = """
            objectClasses:
              User:
                search:
                  endpoints:
                    - path: users/search
                      supportedFilters:
                        - spec: |
                            attribute("id").contains().anySingleValue()
                          request: |
                            request.queryParameter("q", value)
            """;

    /**
     * No {@code request} block: {@code SupportedAttributesHandler} (in {@code
     * com.evolveum.polygon.scimrest.yaml.binding}) only accepts an {@code UpdateOperationBuilder
     * .AttributeSpecific} target, so a YAML {@code create} endpoint's {@code supportedAttributes:}
     * is rejected ("Unknown key 'supportedAttributes' for EndpointImpl") even though {@code
     * RestCreateOperationBuilderImpl.EndpointImpl} has the same {@code supportedAttribute(...)} /
     * content-type-driven auto-body capability as update's endpoint type does - and a custom
     * {@code request.body} is unconditionally rejected too ({@code RestCreateOperationBuilderImpl
     * .RequestBuilderImpl.body()}, "// FIXME: Allow custom implementation"). Real deployed
     * fixtures (e.g. {@code common/.../yaml/Account.create.op.yaml}) use both a body and
     * supportedAttributes together and execute fine - build-time validation currently can't
     * accept either route for a create endpoint's body at all, a real gap left for a separate fix.
     */
    private static final String VALID_CREATE_SCRIPT = """
            objectClasses:
              User:
                create:
                  endpoints:
                    - method: POST
                      path: users
            """;

    private static final String VALID_UPDATE_SCRIPT = """
            objectClasses:
              User:
                update:
                  endpoints:
                    - method: PATCH
                      path: users/{id}
                      request:
                        contentType: application/json
                      supportedAttributes:
                        - id
            """;

    private static final String VALID_DELETE_SCRIPT = """
            objectClasses:
              User:
                delete:
                  endpoints:
                    - method: DELETE
                      path: users/{id}
            """;

    /** Mirrors the shape of {@code connector/forgejo/src/main/resources/authorization.op.yaml}. */
    private static final String VALID_AUTHENTICATION_SCRIPT = """
            authentication:
              rest:
                bearer:
                  implementation: |
                    request.header("Authorization", "token " + decrypt(configuration.restTokenValue))
            """;

    /**
     * Same content as {@link #VALID_OPERATION_SCRIPT}, except the {@code objectClasses} key is
     * lower-cased while the connector's own schema registers the object class as {@code User}
     * (see {@link TestConnector#initializeSchema}) - a regression check for case-insensitive
     * object-class matching specifically on the validation/build path (loading-time case
     * insensitivity is covered separately by {@code YamlSearchOperationTest}).
     */
    private static final String VALID_OPERATION_SCRIPT_LOWERCASE_OBJECT_CLASS = """
            objectClasses:
              user:
                search:
                  endpoints:
                    - path: users
                      emptyFilterSupported: true
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

    @Test
    public void supportedFilterSpecWithUnknownAttributeFailsBuildWithDescriptiveError() {
        var result = validate(
                OPERATION_SCRIPT_WITH_UNKNOWN_FILTER_ATTRIBUTE, "operation", ScriptValidationRequest.SCRIPT_OPERATION_BUILD);

        assertEquals(result.get("status"), "error", "Unexpected result: " + result);
        var message = String.valueOf(result.get("message"));
        assertTrue(message.contains("Attribute 'missing' not found in object class 'User'"),
                "Unexpected message: " + message);
        assertTrue(message.contains("Available attributes"));
    }

    @Test
    public void validYamlSearchByIdScriptPassesValidation() {
        var result = validate(VALID_SEARCH_BY_ID_SCRIPT, "operation", ScriptValidationRequest.SCRIPT_OPERATION_BUILD);

        assertEquals(result.get("status"), "ok", "Unexpected result: " + result);
    }

    @Test
    public void validYamlSearchFilterScriptPassesValidation() {
        var result = validate(VALID_SEARCH_FILTER_SCRIPT, "operation", ScriptValidationRequest.SCRIPT_OPERATION_BUILD);

        assertEquals(result.get("status"), "ok", "Unexpected result: " + result);
    }

    @Test
    public void validYamlCreateScriptPassesValidation() {
        var result = validate(VALID_CREATE_SCRIPT, "operation", ScriptValidationRequest.SCRIPT_OPERATION_BUILD);

        assertEquals(result.get("status"), "ok", "Unexpected result: " + result);
    }

    @Test
    public void validYamlUpdateScriptPassesValidation() {
        var result = validate(VALID_UPDATE_SCRIPT, "operation", ScriptValidationRequest.SCRIPT_OPERATION_BUILD);

        assertEquals(result.get("status"), "ok", "Unexpected result: " + result);
    }

    @Test
    public void validYamlDeleteScriptPassesValidation() {
        var result = validate(VALID_DELETE_SCRIPT, "operation", ScriptValidationRequest.SCRIPT_OPERATION_BUILD);

        assertEquals(result.get("status"), "ok", "Unexpected result: " + result);
    }

    @Test
    public void validYamlAuthenticationScriptPassesValidation() {
        var result = validate(VALID_AUTHENTICATION_SCRIPT, "operation", ScriptValidationRequest.SCRIPT_OPERATION_BUILD);

        assertEquals(result.get("status"), "ok", "Unexpected result: " + result);
    }

    @Test
    public void lowercaseObjectClassKeyStillMatchesAgainstUpperCaseSchemaAtBuildTime() {
        var result = validate(
                VALID_OPERATION_SCRIPT_LOWERCASE_OBJECT_CLASS, "operation", ScriptValidationRequest.SCRIPT_OPERATION_BUILD);

        assertEquals(result.get("status"), "ok", "Unexpected result: " + result);
    }

    @SuppressWarnings("unchecked")
    private static void assertFirstErrorPhaseAndSource(Map<String, Object> result, String phase, String source) {
        assertEquals(result.get("status"), "error", "Unexpected result: " + result);
        var errors = (List<Map<String, Object>>) result.get("errors");
        assertEquals(errors.getFirst().get("phase"), phase, "Unexpected result: " + result);
        assertEquals(errors.getFirst().get("source"), source, "Unexpected result: " + result);
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
