/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy.handler;

import com.evolveum.polygon.scimrest.groovy.schema.BaseOperationSupportBuilder;
import com.evolveum.polygon.scimrest.groovy.connector.RestConnectorContext;

import com.evolveum.polygon.conndev.annotations.Script;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import com.evolveum.polygon.conndev.groovy.GroovyContext;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import groovy.lang.GroovyShell;
import org.codehaus.groovy.runtime.MethodClosure;

import java.io.InputStreamReader;

public class GroovyRestHandlerBuilder extends RestHandlerBuilder {

    private final GroovyShell shell;

    public GroovyRestHandlerBuilder(GroovyContext context, RestConnectorContext schema) {
        super(schema);
        this.shell = context.createShell();
        shell.setVariable("objectClass", new MethodClosure(this, "objectClass"));
        shell.setVariable("test", new MethodClosure(this, "test"));
        shell.setVariable("authentication", new MethodClosure(this, "authentication"));

    }

    public void loadFromResource(String s) {
        var stream = this.getClass().getResourceAsStream(s);
        if (stream == null) {
            // A missing script resource used to fail with a bare NPE inside the shell.
            throw new ConfigurationException("Connector script resource not found: " + s);
        }
        shell.evaluate(new InputStreamReader(stream), s);
    }

    public void loadFromString(String script) {
        shell.evaluate(script);
    }

    public groovy.lang.Script parse(String script) {
        return shell.parse(script);
    }

    public BaseOperationSupportBuilder objectClass(String name, @DelegatesTo(BaseOperationSupportBuilder.class) @Script.Initialization Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, objectClass(name));
    }
}
