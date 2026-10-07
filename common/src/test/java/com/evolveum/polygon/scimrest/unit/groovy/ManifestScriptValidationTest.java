/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.unit.groovy;

import com.evolveum.polygon.scimrest.groovy.impl.ManifestBasedConnector;
import com.evolveum.polygon.scimrest.groovy.impl.ReadOnlyConfiguration;
import org.identityconnectors.framework.common.objects.ScriptContext;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;

import static org.testng.Assert.assertEquals;

/**
 * Script validation against a real {@link ManifestBasedConnector}, with sibling scripts actually
 * deployed on the classpath and reloaded around the candidate (exclusion itself is covered by
 * conndev's {@code ConnectorManifestTest}). Request/response are written as JSON trees, compared
 * structurally rather than as raw strings, so the expected JSON can stay naturally formatted.
 */
public class ManifestScriptValidationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String MANIFEST_BASE = "/manifests/script-validation/connector.manifest";
    private static final String OPERATION_SCRIPT_RESOURCE = "/manifests/script-validation/Account.op.groovy";
    private static final String SCHEMA_SCRIPT_RESOURCE = "/manifests/script-validation/Account.schema.groovy";

    private static final String CROSS_CHECK_MANIFEST_BASE = "/manifests/script-validation-cross-check/connector.manifest";
    private static final String CROSS_CHECK_SCHEMA_SCRIPT_RESOURCE = "/manifests/script-validation-cross-check/Account.schema.groovy";

    private static final String MULTI_OVERRIDE_MANIFEST_BASE = "/manifests/script-validation-multi-override/connector.manifest";
    private static final String MULTI_OVERRIDE_ACCOUNT_RESOURCE = "/manifests/script-validation-multi-override/Account.schema.groovy";
    private static final String MULTI_OVERRIDE_GROUP_RESOURCE = "/manifests/script-validation-multi-override/Group.schema.groovy";

    private static class TestConnector extends ManifestBasedConnector {
        TestConnector() {
            this(MANIFEST_BASE);
        }

        TestConnector(String manifestBase) {
            super(manifestBase);
            var config = new ReadOnlyConfiguration();
            config.setBaseAddress("http://localhost");
            config.setDevelopmentMode(true);
            init(config);
        }
    }

    @Test
    public void validatingReplacementForExistingOperationScriptSucceeds() {
        var result = validate(
                "objectClass('Account') { search { endpoint('/accounts') { } } }", """
                {
                  "operation": "build",
                  "artifactKind": "operation",
                  "filename": "%s"
                }
                """.formatted(OPERATION_SCRIPT_RESOURCE));

        assertEquals(result, json("""
                { "status": "ok" }
                """));
    }

    @Test
    public void validatingSchemaCandidateWithSiblingLoadedSucceeds() {
        var result = validate(
                "objectClass('Account') { attribute('id').connId().type(String.class); attribute('name').connId().type(String.class) }",
                """
                {
                  "operation": "build",
                  "artifactKind": "schema",
                  "filename": "%s"
                }
                """.formatted(SCHEMA_SCRIPT_RESOURCE));

        assertEquals(result, json("""
                { "status": "ok" }
                """));
    }

    @Test
    public void validatingNewOperationScriptForNotYetDeployedObjectClassSucceeds() {
        var result = validate(
                "objectClass('Group') { search { endpoint('/groups') { } } }", """
                {
                  "operation": "build",
                  "artifactKind": "operation"
                }
                """);

        assertEquals(result, json("""
                { "status": "ok" }
                """));
    }

    /**
     * No overrides, no broken siblings - just the primary candidate itself failing on its own.
     * The result must still be the unified "errors" array shape, not the older flat one, even
     * though there is exactly one error and nothing was combined with it.
     */
    @Test
    public void validatingBrokenPrimaryWithNoOverridesStillReturnsCombinedArrayShape() {
        var result = validate(
                "objectClass('Group') { search { endpoint('/groups'", """
                {
                  "operation": "build",
                  "artifactKind": "operation"
                }
                """);

        assertEquals(result, json("""
                {
                  "status": "error",
                  "errors": [
                    {
                      "status": "error",
                      "phase": "compile",
                      "message": "startup failed:\\nScript1.groovy: 1: Missing ')' @ line 1, column 51.\\n   objectClass('Group') { search { endpoint('/groups'\\n                                                     ^\\n\\n1 error\\n",
                      "line": 1,
                      "column": 51
                    }
                  ]
                }
                """));
    }

    @Test
    public void validatingSchemaCandidateThatDropsAttributeFailsDependentOperationScript() {
        var connector = new TestConnector(CROSS_CHECK_MANIFEST_BASE);

        var result = validate(connector,
                "objectClass('Account') { attribute('id').connId().type(String.class) }", """
                {
                  "operation": "build",
                  "artifactKind": "schema",
                  "filename": "%s"
                }
                """.formatted(CROSS_CHECK_SCHEMA_SCRIPT_RESOURCE));

        assertEquals(result, json("""
                {
                  "status": "error",
                  "errors": [
                    {
                      "status": "error",
                      "phase": "evaluate",
                      "message": "Account.op.groovy: Attribute 'email' not found in object class 'Account' when defining a custom search filter. Available attributes: id",
                      "line": 1,
                      "source": "Account.op.groovy"
                    }
                  ]
                }
                """));
    }

    /**
     * "Repair object class" can regenerate several scripts in one response. Validating one of
     * the candidates (Account) with the other (Group) carried as an extra {@code overrides}
     * entry must succeed even though Group's deployed version is broken - its candidate content
     * stands in for it instead of reloading the broken sibling from disk.
     */
    @Test
    public void validatingOneCandidateWithAnotherOverriddenSucceedsDespiteBothBeingBrokenOnDisk() {
        var connector = new TestConnector(MULTI_OVERRIDE_MANIFEST_BASE);

        var result = validate(connector,
                "objectClass('Account') { attribute('id').connId().type(String.class) }", """
                {
                  "operation": "build",
                  "artifactKind": "schema",
                  "filename": "%s",
                  "overrides": {
                    "%s": "objectClass('Group') { attribute('id').connId().type(String.class) }"
                  }
                }
                """.formatted(MULTI_OVERRIDE_ACCOUNT_RESOURCE, MULTI_OVERRIDE_GROUP_RESOURCE));

        assertEquals(result, json("""
                { "status": "ok" }
                """));
    }

    /**
     * Same fixture, but WITHOUT overriding the Group sibling - it stays broken on disk, gets
     * reloaded, and fails the build: the chicken-and-egg problem {@code overrides} fixes, where a
     * multi-script repair batch would otherwise never validate as a set.
     */
    @Test
    public void validatingOneCandidateAloneFailsWhileASiblingIsStillBrokenOnDisk() {
        var connector = new TestConnector(MULTI_OVERRIDE_MANIFEST_BASE);

        var result = validate(connector,
                "objectClass('Account') { attribute('id').connId().type(String.class) }", """
                {
                  "operation": "build",
                  "artifactKind": "schema",
                  "filename": "%s"
                }
                """.formatted(MULTI_OVERRIDE_ACCOUNT_RESOURCE));

        assertEquals(result, json("""
                {
                  "status": "error",
                  "errors": [
                    {
                      "status": "error",
                      "phase": "initialization",
                      "message": "startup failed:\\n/manifests/script-validation-multi-override/Group.schema.groovy: 3: Missing ')' @ line 3, column 19.\\n       attribute(\\"id\\"\\n                     ^\\n\\n1 error\\n",
                      "line": 3,
                      "column": 19
                    }
                  ]
                }
                """));
    }

    /**
     * A batch can have more than one broken override at once. Each broken one must get its own
     * error entry in the combined result - not just the first one encountered, with the rest
     * silently skipped.
     */
    @Test
    public void validatingWithTwoBrokenOverridesReportsAnErrorForEachOne() {
        var connector = new TestConnector(MULTI_OVERRIDE_MANIFEST_BASE);

        var result = validate(connector,
                "objectClass('Account') { attribute('id').connId().type(String.class) }", """
                {
                  "operation": "build",
                  "artifactKind": "schema",
                  "filename": "%s",
                  "overrides": {
                    "%s": "objectClass('Group') { attribute('id'",
                    "/manifests/script-validation-multi-override/Role.schema.groovy": "objectClass('Role') { attribute('id'"
                  }
                }
                """.formatted(MULTI_OVERRIDE_ACCOUNT_RESOURCE, MULTI_OVERRIDE_GROUP_RESOURCE));

        // overrides map iteration order isn't guaranteed, so errors are compared sorted by source
        assertEquals(sortedBySource(result), sortedBySource(json("""
                {
                  "status": "error",
                  "errors": [
                    {
                      "status": "error",
                      "phase": "evaluate",
                      "message": "startup failed:\\nScript2.groovy: 1: Missing ')' @ line 1, column 38.\\n   objectClass('Group') { attribute('id'\\n                                        ^\\n\\n1 error\\n",
                      "line": 1,
                      "column": 38,
                      "source": "%s"
                    },
                    {
                      "status": "error",
                      "phase": "evaluate",
                      "message": "startup failed:\\nScript1.groovy: 1: Missing ')' @ line 1, column 37.\\n   objectClass('Role') { attribute('id'\\n                                       ^\\n\\n1 error\\n",
                      "line": 1,
                      "column": 37,
                      "source": "/manifests/script-validation-multi-override/Role.schema.groovy"
                    }
                  ]
                }
                """.formatted(MULTI_OVERRIDE_GROUP_RESOURCE))));
    }

    /**
     * An operation override unrelated to the primary candidate is broken. The primary itself is
     * fine, but the batch as a whole isn't - the broken override is reported too, attributed to
     * its own filename, instead of being silently dropped just because the primary succeeded.
     */
    @Test
    public void validatingOperationCandidateStillReportsAnUnrelatedBrokenOperationOverride() {
        var result = validate(
                "objectClass('Account') { search { endpoint('/accounts') { } } }", """
                {
                  "operation": "build",
                  "artifactKind": "operation",
                  "filename": "%s",
                  "overrides": {
                    "/manifests/script-validation/Group.create.op.groovy": "objectClass('Group') { create { endpoint('/groups'"
                  }
                }
                """.formatted(OPERATION_SCRIPT_RESOURCE));

        assertEquals(result, json("""
                {
                  "status": "error",
                  "errors": [
                    {
                      "status": "error",
                      "phase": "evaluate",
                      "message": "startup failed:\\nScript1.groovy: 1: Missing ')' @ line 1, column 51.\\n   objectClass('Group') { create { endpoint('/groups'\\n                                                     ^\\n\\n1 error\\n",
                      "line": 1,
                      "column": 51,
                      "source": "/manifests/script-validation/Group.create.op.groovy"
                    }
                  ]
                }
                """));
    }

    private static JsonNode json(String json) {
        return MAPPER.readTree(json);
    }

    /** Overrides load in an unspecified order - sort the "errors" array by "source" so comparisons don't depend on it. */
    private static JsonNode sortedBySource(JsonNode node) {
        if (!node.isObject() || !node.has("errors")) {
            return node;
        }
        var errors = new ArrayList<JsonNode>();
        node.get("errors").forEach(errors::add);
        errors.sort(Comparator.comparing(e -> e.get("source").asString()));
        var sorted = ((tools.jackson.databind.node.ObjectNode) node.deepCopy());
        var array = MAPPER.createArrayNode();
        errors.forEach(array::add);
        sorted.set("errors", array);
        return sorted;
    }

    private static JsonNode validate(String script, String argumentsJson) {
        return validate(new TestConnector(), script, argumentsJson);
    }

    @SuppressWarnings("unchecked")
    private static JsonNode validate(TestConnector connector, String script, String argumentsJson) {
        Map<String, Object> arguments = MAPPER.readValue(argumentsJson, Map.class);
        var context = new ScriptContext("groovy", script, arguments);
        Object result = connector.runScriptOnResource(context, null);
        return MAPPER.valueToTree(result);
    }
}
