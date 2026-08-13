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
import java.util.Map;

import static org.testng.Assert.assertEquals;

/**
 * Script validation against a real {@link ManifestBasedConnector}, where sibling scripts are
 * actually deployed on the classpath and reloaded around the candidate. Direct coverage of the
 * exclusion itself lives in conndev's {@code ConnectorManifestTest}.
 */
public class ManifestScriptValidationTest {

    private static final String MANIFEST_BASE = "/manifests/script-validation/connector.manifest";
    private static final String OPERATION_SCRIPT_RESOURCE = "/manifests/script-validation/Account.op.groovy";
    private static final String SCHEMA_SCRIPT_RESOURCE = "/manifests/script-validation/Account.schema.groovy";

    private static final String CROSS_CHECK_MANIFEST_BASE = "/manifests/script-validation-cross-check/connector.manifest";
    private static final String CROSS_CHECK_SCHEMA_SCRIPT_RESOURCE = "/manifests/script-validation-cross-check/Account.schema.groovy";

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
                "objectClass('Account') { search { endpoint('/accounts') { } } }", "operation", OPERATION_SCRIPT_RESOURCE);

        assertEquals(result.get("status"), "ok", "Unexpected result: " + result);
    }

    @Test
    public void validatingSchemaCandidateWithSiblingLoadedSucceeds() {
        var result = validate(
                "objectClass('Account') { attribute('id').connId().type(String.class); attribute('name').connId().type(String.class) }",
                "schema", SCHEMA_SCRIPT_RESOURCE);

        assertEquals(result.get("status"), "ok", "Unexpected result: " + result);
    }

    @Test
    public void validatingNewOperationScriptForNotYetDeployedObjectClassSucceeds() {
        var result = validate(
                "objectClass('Group') { search { endpoint('/groups') { } } }", "operation", null);

        assertEquals(result.get("status"), "ok", "Unexpected result: " + result);
    }

    @Test
    public void validatingSchemaCandidateThatDropsAttributeFailsDependentOperationScript() {
        var connector = new TestConnector(CROSS_CHECK_MANIFEST_BASE);

        var result = validate(connector,
                "objectClass('Account') { attribute('id').connId().type(String.class) }",
                "schema", CROSS_CHECK_SCHEMA_SCRIPT_RESOURCE);

        assertEquals(result.get("status"), "error", "Unexpected result: " + result);
        @SuppressWarnings("unchecked")
        var errors = (java.util.List<Map<String, Object>>) result.get("errors");
        assertEquals(errors.size(), 1, "Unexpected errors: " + errors);
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
        var context = new ScriptContext("groovy", script, arguments);
        return (Map<String, Object>) connector.runScriptOnResource(context, null);
    }
}
