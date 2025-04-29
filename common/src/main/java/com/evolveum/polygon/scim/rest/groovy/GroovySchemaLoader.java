package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.schema.RestSchema;
import com.evolveum.polygon.scim.rest.schema.RestSchemaBuilder;
import groovy.lang.GroovyShell;
import org.codehaus.groovy.runtime.MethodClosure;

public class GroovySchemaLoader {

    private GroovyShell shell;
    RestSchemaBuilder schemaBuilder;

    public GroovySchemaLoader(GroovyContext context, RestSchemaBuilder schemaBuilder) {
        shell = context.createShell();
        this.schemaBuilder = schemaBuilder;
        shell.setVariable("objectClass", new MethodClosure(schemaBuilder, "objectClass"));
    }

    public void load(String groovyScript) {
        shell.evaluate(groovyScript);
    }

    public RestSchema build() {
        return schemaBuilder.build();
    }
}
