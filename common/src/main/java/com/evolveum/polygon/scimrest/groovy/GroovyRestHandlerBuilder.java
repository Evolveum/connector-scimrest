/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import groovy.lang.GroovyShell;
import org.codehaus.groovy.runtime.MethodClosure;

import java.io.InputStreamReader;

public class GroovyRestHandlerBuilder extends RestHandlerBuilder {


    private final GroovyShell shell;

    public GroovyRestHandlerBuilder(GroovyContext context, ConnectorContext schema) {
        super(schema);
        this.shell = context.createShell();
        shell.setVariable("objectClass", new MethodClosure(this, "objectClass"));
    }

    public void loadFromResource(String s) {
        shell.evaluate(new InputStreamReader(this.getClass().getResourceAsStream(s)), s);
    }

    public void loadFromString(String script) {
        shell.evaluate(script);
    }

    public BaseOperationSupportBuilder objectClass(String name, @DelegatesTo(BaseOperationSupportBuilder.class) Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, objectClass(name));
    }
}
