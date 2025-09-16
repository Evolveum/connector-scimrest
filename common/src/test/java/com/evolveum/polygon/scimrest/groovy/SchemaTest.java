/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy;

import com.evolveum.polygon.scimrest.schema.RestSchemaBuilder;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.assertNotNull;

public class SchemaTest {
    @Test
    public void testSchemaLoading() {
        var context = new GroovyContext();
        var schema = new RestSchemaBuilder(AbstractGroovyRestConnector.class, null);
        var loader = new GroovySchemaLoader(context, schema);
        loader.load("""
               objectClass("user") {
                  attribute("id") {
                    jsonType "string";
                    openApiFormat "email";
                  }
               }
        """);
        assertNotNull(schema);
    }


}
