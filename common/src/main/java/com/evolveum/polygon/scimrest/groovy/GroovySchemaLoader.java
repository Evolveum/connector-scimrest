/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy;

import com.evolveum.polygon.scimrest.schema.RestSchema;
import com.evolveum.polygon.scimrest.schema.RestSchemaBuilder;
import groovy.lang.GroovyShell;
import org.codehaus.groovy.runtime.MethodClosure;

import java.io.InputStreamReader;

public class GroovySchemaLoader {

    private GroovyShell shell;
    RestSchemaBuilder schemaBuilder;

    public GroovySchemaLoader(GroovyContext context, RestSchemaBuilder schemaBuilder) {
        shell = context.createShell();
        this.schemaBuilder = schemaBuilder;
        shell.setVariable("objectClass", new MethodClosure(schemaBuilder, "objectClass"));
        shell.setVariable("relationship", new MethodClosure(schemaBuilder, "relationship"));

    }

    public void load(String groovyScript) {
        shell.evaluate(groovyScript);
    }

    public RestSchema build() {
        return schemaBuilder.build();
    }

    public void loadFromResource(String s) {
        shell.evaluate(new InputStreamReader(this.getClass().getResourceAsStream(s)), s);
    }
}
