/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.unit.groovy;

import com.evolveum.polygon.conndev.groovy.ScriptValidationRequest;
import com.evolveum.polygon.scimrest.groovy.impl.ManifestBasedConnector;
import com.evolveum.polygon.scimrest.groovy.impl.ReadOnlyConfiguration;
import org.identityconnectors.framework.common.objects.ScriptContext;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;

/**
 * YAML counterpart of {@link ManifestScriptValidationTest}: script validation against a real
 * {@link ManifestBasedConnector}, with a YAML operation script sibling actually deployed on the
 * classpath and reloaded around the candidate - the real-deployment shape, not an isolated
 * inline-schema connector like {@link YamlScriptValidationTest} uses. (The schema siblings are
 * Groovy - see the comment on {@link #MANIFEST_BASE} for why.)
 */
public class YamlManifestScriptValidationTest {

    // The schema siblings deployed here are Groovy; the scripts under *validation* (the candidate
    // content passed to validate() below, and the YAML operation sibling) are genuinely YAML.
    private static final String MANIFEST_BASE = "/manifests/script-validation-yaml/connector.manifest";
    private static final String OPERATION_SCRIPT_RESOURCE = "/manifests/script-validation-yaml/Account.op.yaml";
    private static final String SCHEMA_SCRIPT_RESOURCE = "/manifests/script-validation-yaml/Account.schema.groovy";

    private static final String CROSS_CHECK_MANIFEST_BASE = "/manifests/script-validation-cross-check-yaml/connector.manifest";
    private static final String CROSS_CHECK_SCHEMA_SCRIPT_RESOURCE = "/manifests/script-validation-cross-check-yaml/Account.schema.groovy";

    // Group's schema is a YAML sibling here (unlike MANIFEST_BASE's Groovy-only schema siblings -
    // see the comment there): this manifest exists specifically to prove that a YAML schema
    // sibling's object class is visible to candidate-schema validation, not just to a real
    // connector's own bootstrap.
    private static final String YAML_SIBLING_MANIFEST_BASE = "/manifests/script-validation-yaml-sibling/connector.manifest";
    private static final String YAML_SIBLING_SCHEMA_SCRIPT_RESOURCE = "/manifests/script-validation-yaml-sibling/Account.schema.groovy";

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
        var result = validate("""
                objectClasses:
                  Account:
                    search:
                      endpoints:
                        - path: /accounts
                """, "operation", OPERATION_SCRIPT_RESOURCE);

        assertEquals(result.get("status"), "ok", "Unexpected result: " + result);
    }

    @Test
    public void validatingSchemaCandidateWithSiblingLoadedSucceeds() {
        var result = validate("""
                objectClasses:
                  Account:
                    attributes:
                      id:
                        connId:
                          type: String
                      name:
                        connId:
                          type: String
                """, "schema", SCHEMA_SCRIPT_RESOURCE);

        assertEquals(result.get("status"), "ok", "Unexpected result: " + result);
    }

    @Test
    public void validatingNewOperationScriptForNotYetDeployedObjectClassSucceeds() {
        var result = validate("""
                objectClasses:
                  Group:
                    search:
                      endpoints:
                        - path: /groups
                """, "operation", null);

        assertEquals(result.get("status"), "ok", "Unexpected result: " + result);
    }

    @Test
    public void validatingSchemaCandidateThatDropsAttributeFailsDependentOperationScript() {
        var connector = new TestConnector(CROSS_CHECK_MANIFEST_BASE);

        var result = validate(connector, """
                objectClasses:
                  Account:
                    attributes:
                      id:
                        connId:
                          type: String
                """, "schema", CROSS_CHECK_SCHEMA_SCRIPT_RESOURCE);

        assertEquals(result.get("status"), "error", "Unexpected result: " + result);
        @SuppressWarnings("unchecked")
        var errors = (List<Map<String, Object>>) result.get("errors");
        assertEquals(errors.size(), 1, "Unexpected errors: " + errors);
    }

    @Test
    public void validatingSchemaCandidateSeesObjectClassFromYamlSibling() {
        // Group only exists via the deployed YAML schema sibling (Group.schema.yaml) - and
        // Group.op.yaml (a deployed operation script targeting Group) gets re-validated against
        // this Account candidate's merged schema by validateOperationsAgainstCandidateSchema().
        // If the YAML sibling's object class didn't make it into the candidate schema, Group.op.yaml
        // would fail to build against it (unknown object class) and this would report "error".
        var connector = new TestConnector(YAML_SIBLING_MANIFEST_BASE);

        var result = validate(connector, """
                objectClasses:
                  Account:
                    attributes:
                      id:
                        connId:
                          type: String
                """, "schema", YAML_SIBLING_SCHEMA_SCRIPT_RESOURCE);

        assertEquals(result.get("status"), "ok", "Unexpected result: " + result);
    }

    private static Map<String, Object> validate(String script, String artifactKind, String filename) {
        return validate(new TestConnector(), script, artifactKind, filename);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> validate(TestConnector connector, String script, String artifactKind, String filename) {
        var arguments = new HashMap<String, Object>();
        arguments.put(ScriptValidationRequest.SCRIPT_ARGUMENT_OPERATION, ScriptValidationRequest.SCRIPT_OPERATION_BUILD);
        arguments.put(ScriptValidationRequest.SCRIPT_ARGUMENT_ARTIFACT_KIND, artifactKind);
        if (filename != null) {
            arguments.put(ScriptValidationRequest.SCRIPT_ARGUMENT_FILENAME, filename);
        }
        var context = new ScriptContext("yaml", script, arguments);
        return (Map<String, Object>) connector.runScriptOnResource(context, null);
    }
}
