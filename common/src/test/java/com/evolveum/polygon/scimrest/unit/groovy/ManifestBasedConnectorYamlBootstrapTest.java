/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.unit.groovy;

import com.evolveum.polygon.scimrest.groovy.impl.ManifestBasedConnector;
import com.evolveum.polygon.scimrest.groovy.impl.ReadOnlyConfiguration;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.testng.annotations.Test;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertEquals;

/**
 * Exercises a {@link ManifestBasedConnector}'s real (non-validation) bootstrap - {@code
 * schema()}/{@code handlerFor()}, driven by {@code initializeCore0()}/{@code initializeHandlers0()}
 * - with a YAML schema script and a YAML operation script, as opposed to the validation-only path
 * already covered by {@link YamlManifestScriptValidationTest}.
 *
 * <p><b>{@link #yamlOperationScriptDrivesHandlerThroughRealBootstrap()}</b>: {@link
 * com.evolveum.polygon.scimrest.groovy.handler.HandlerDefinitionBuilder} (used for both
 * authorization and object-class operation scripts) is a single builder with an overridden {@code
 * loadFromResource} that dispatches per extension - Groovy and YAML scripts both feed the same
 * {@code RestHandlerBuilder}, so this already worked correctly for real (not just validation-time)
 * even before the schema-side fix below.
 *
 * <p><b>{@link #yamlSchemaScriptDrivesSchemaThroughRealBootstrap()}</b>: previously documented a
 * real, unfixed gap - {@code SchemaDefinitionLoader} (used only for schema scripts) parsed a YAML
 * schema resource onto a <i>separate</i>, context-less throwaway builder rather than the shared
 * {@code RestSchemaBuilderImpl} {@code initializeCore0()} builds {@code context.schema()} from, and
 * nothing ever merged the two back together for any connector, SCIM-enabled or not. Fixed by having
 * {@code SchemaDefinitionLoader.loadFromResource()} itself merge a YAML resource's object class
 * definitions into the shared builder immediately (via the new {@code
 * BaseSchemaBuilder#defineObjectClass(BaseObjectClassDefinition)} overload) - symmetric with how the
 * Groovy branch already worked, so callers need no separate step for YAML either.
 */
public class ManifestBasedConnectorYamlBootstrapTest {

    private static final String ALL_YAML_MANIFEST_BASE = "/manifests/real-bootstrap-yaml/connector.manifest";
    private static final String GROOVY_SCHEMA_MANIFEST_BASE = "/manifests/real-bootstrap-yaml/connector-groovy-schema.manifest";
    private static final String MIXED_SCHEMA_MANIFEST_BASE = "/manifests/real-bootstrap-yaml/connector-mixed-schema.manifest";

    private static class TestConnector extends ManifestBasedConnector {
        TestConnector(String manifestBase) {
            super(manifestBase);
        }
    }

    private static TestConnector initializedConnector(String manifestBase) {
        var connector = new TestConnector(manifestBase);
        var config = new ReadOnlyConfiguration();
        config.setBaseAddress("http://localhost");
        config.setDevelopmentMode(true);
        connector.init(config);
        return connector;
    }

    @Test
    public void yamlOperationScriptDrivesHandlerThroughRealBootstrap() {
        // Groovy schema + YAML operation script - isolates the handler-loading path from schema
        // loading, tested separately below.
        var connector = initializedConnector(GROOVY_SCHEMA_MANIFEST_BASE);

        assertNotNull(connector.schema().findObjectClassInfo("Account"));
        assertNotNull(connector.handlerFor(new ObjectClass("Account")), "YAML operation script wasn't loaded by the real bootstrap");
    }

    @Test
    public void yamlSchemaScriptDrivesSchemaThroughRealBootstrap() {
        var connector = initializedConnector(ALL_YAML_MANIFEST_BASE);

        var objectClassInfo = connector.schema().findObjectClassInfo("Account");
        assertNotNull(objectClassInfo, "YAML schema script wasn't loaded by the real bootstrap");

        // The declared "id" attribute (Account.schema.yaml) is really there - not just present as
        // a bare, mapping-less ObjectClassInfo wrapper (the fallback ScimContext's dev-mode blocks
        // use for ready-made ObjectClassInfo-only contributions).
        AttributeInfo idAttribute = objectClassInfo.getAttributeInfo().stream()
                .filter(a -> "id".equals(a.getNativeName()))
                .findFirst()
                .orElse(null);
        assertNotNull(idAttribute, "Account object class has no 'id' attribute");
        assertEquals(idAttribute.getType(), String.class);

        // And it's dispatchable, not just visible in the schema - the operation script (also YAML,
        // per Account.op.yaml) resolves against the same object class.
        assertNotNull(connector.handlerFor(new ObjectClass("Account")));
    }

    /**
     * One manifest, two object classes, two schema script formats at once - {@code Account} via YAML
     * ({@code Account.schema.yaml}) and {@code Group} via Groovy ({@code Group.schema.groovy}) -
     * through the real bootstrap. Both fixes above are exercised together here: the Groovy branch
     * populates {@code schemaBuilder} directly as it always has, and the merge fix folds the YAML
     * object class in alongside it, so both must be present and correctly typed in one {@code
     * schema()} call.
     */
    @Test
    public void mixedGroovyAndYamlSchemaScriptsBothDriveSchemaThroughRealBootstrap() {
        var connector = initializedConnector(MIXED_SCHEMA_MANIFEST_BASE);
        var schema = connector.schema();

        var accountOci = schema.findObjectClassInfo("Account");
        assertNotNull(accountOci, "YAML-schema'd Account object class wasn't loaded by the real bootstrap");
        AttributeInfo idAttribute = accountOci.getAttributeInfo().stream()
                .filter(a -> "id".equals(a.getNativeName()))
                .findFirst()
                .orElse(null);
        assertNotNull(idAttribute, "Account object class has no 'id' attribute");
        assertEquals(idAttribute.getType(), String.class);

        var groupOci = schema.findObjectClassInfo("Group");
        assertNotNull(groupOci, "Groovy-schema'd Group object class wasn't loaded by the real bootstrap");
        AttributeInfo nameAttribute = groupOci.getAttributeInfo().stream()
                .filter(a -> "name".equals(a.getNativeName()))
                .findFirst()
                .orElse(null);
        assertNotNull(nameAttribute, "Group object class has no 'name' attribute");
        assertEquals(nameAttribute.getType(), String.class);

        // Account is still dispatchable too (its operation script, per the manifest, is unaffected
        // by Group's presence).
        assertNotNull(connector.handlerFor(new ObjectClass("Account")));
    }

    /**
     * Same manifest as above, but for the *operation* side: {@code Account.op.yaml} (YAML) and
     * {@code Group.op.groovy} (Groovy) both bundled together. Unlike schema loading,
     * {@code HandlerDefinitionBuilder} never had a separate-builder gap - both formats already feed
     * the same {@code RestHandlerBuilder} via its own per-extension {@code loadFromResource}
     * dispatch - so this is confirming existing behavior under a genuinely mixed manifest, not a fix.
     */
    @Test
    public void mixedGroovyAndYamlOperationScriptsBothDriveHandlersThroughRealBootstrap() {
        var connector = initializedConnector(MIXED_SCHEMA_MANIFEST_BASE);

        assertNotNull(connector.handlerFor(new ObjectClass("Account")),
                "YAML operation script (Account.op.yaml) wasn't loaded by the real bootstrap");
        assertNotNull(connector.handlerFor(new ObjectClass("Group")),
                "Groovy operation script (Group.op.groovy) wasn't loaded by the real bootstrap");
    }
}
