package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.RestContext;
import com.evolveum.polygon.scim.rest.schema.RestSchema;
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
        shell.evaluate(new InputStreamReader(this.getClass().getResourceAsStream(s)));
    }

    public void loadFromString(String script) {
        shell.evaluate(script);
    }

    public BaseOperationSupportBuilder<RestContext> objectClass(String name, @DelegatesTo(BaseOperationSupportBuilder.class) Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, objectClass(name));
    }
}
