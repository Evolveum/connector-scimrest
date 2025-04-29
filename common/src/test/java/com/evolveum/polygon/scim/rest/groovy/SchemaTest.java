package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.schema.RestSchemaBuilder;
import groovy.lang.Binding;
import groovy.lang.Closure;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import groovy.util.GroovyScriptEngine;
import groovy.util.ResourceConnector;
import groovy.util.ResourceException;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.runtime.MethodClosure;
import org.testng.annotations.Test;

import java.net.URLConnection;

import static org.testng.AssertJUnit.assertNotNull;

public class SchemaTest {
    @Test
    public void testSchemaLoading() {

        var context = new GroovyContext();
        var schema = new RestSchemaBuilder();
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
