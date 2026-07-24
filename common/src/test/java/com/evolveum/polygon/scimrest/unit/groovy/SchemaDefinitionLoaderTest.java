/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.unit.groovy;

import com.evolveum.polygon.scimrest.groovy.AbstractGroovyRestConnector;
import com.evolveum.polygon.scimrest.groovy.GroovyContext;
import com.evolveum.polygon.scimrest.groovy.SchemaDefinitionLoader;
import com.evolveum.polygon.scimrest.schema.RestSchemaBuilderImpl;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertNull;
import static org.testng.AssertJUnit.assertTrue;

public class SchemaDefinitionLoaderTest {

    private SchemaDefinitionLoader loader() {
        return new SchemaDefinitionLoader(new GroovyContext(),
                new RestSchemaBuilderImpl(AbstractGroovyRestConnector.class, null));
    }

    /** A Groovy definition missing from the bundle falls back to the YAML document of the same name. */
    @Test
    public void missingGroovyDefinitionFallsBackToYaml() {
        var loader = loader();
        loader.loadFromResource("/yaml/TestUser.native.schema.groovy");

        var baseSchema = loader.baseSchema();
        assertNotNull(baseSchema);
        assertNotNull(baseSchema.objectClass("TestUser"));
    }

    @Test
    public void yamlDefinitionBuildsInertBaseSchema() {
        var loader = loader();
        loader.loadFromResource("/yaml/TestUser.native.schema.yaml");

        var baseSchema = loader.baseSchema();
        assertNotNull(baseSchema);
        var objectClass = baseSchema.objectClass("TestUser");
        assertNotNull(objectClass);
        assertTrue(objectClass.attributeFromProtocolName("login").connId().isRequired());

        // the functional schema is untouched by YAML definitions
        assertTrue(loader.build().objectClasses().stream()
                .noneMatch(oc -> "TestUser".equals(oc.objectClass().getObjectClassValue())));
    }

    @Test
    public void baseSchemaIsNullWithoutYamlDefinitions() {
        var loader = loader();
        loader.load("""
               objectClass("user") {
                  attribute("id") {
                    jsonType "string";
                  }
               }
        """);
        assertNull(loader.baseSchema());
    }
}
