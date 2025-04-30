package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.schema.RestSchemaBuilder;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.assertNotNull;

public class SchemaTest {
    @Test
    public void testSchemaLoading() {

        var context = new GroovyContext();
        var schema = new RestSchemaBuilder(AbstractGroovyRestConnector.class);
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
