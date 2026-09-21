/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.unit.groovy;

import com.evolveum.polygon.scimrest.groovy.connector.AbstractGroovyRestConnector;
import com.evolveum.polygon.conndev.groovy.GroovyContext;
import com.evolveum.polygon.scimrest.groovy.schema.SchemaDefinitionLoader;
import com.evolveum.polygon.scimrest.schema.RestSchemaBuilderImpl;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertTrue;

public class SchemaDefinitionLoaderTest {

    private SchemaDefinitionLoader loader() {
        return new SchemaDefinitionLoader(new GroovyContext(),
                new RestSchemaBuilderImpl(AbstractGroovyRestConnector.class, null));
    }

    /**
     * A Groovy definition missing from the bundle falls back to the YAML document of the same
     * name, and that fallback ends up in the functional schema exactly like a Groovy definition
     * would.
     */
    @Test
    public void missingGroovyDefinitionFallsBackToYaml() {
        var loader = loader();
        loader.loadFromResource("/yaml/TestUser.native.schema.groovy");

        assertNotNull(loader.build().objectClass("TestUser"));
    }

    /**
     * loadFromResource() is symmetric across formats: a YAML schema definition's object class ends
     * up directly in the functional schema {@link SchemaDefinitionLoader#build()} returns, with the
     * same real attribute mappings a Groovy definition would have - no separate step needed.
     */
    @Test
    public void yamlDefinitionMergesIntoFunctionalSchema() {
        var loader = loader();
        loader.loadFromResource("/yaml/TestUser.native.schema.yaml");

        var objectClass = loader.build().objectClass("TestUser");
        assertNotNull(objectClass);
        assertTrue(objectClass.attributeFromProtocolName("login").connId().isRequired());
    }
}
