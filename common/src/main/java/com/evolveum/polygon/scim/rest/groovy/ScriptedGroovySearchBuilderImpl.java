package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.schema.RestObjectClass;
import groovy.lang.Closure;

public class ScriptedGroovySearchBuilderImpl<HC> implements ScriptedGroovySearchBuilder, FilterAwareSearchProcessorBuilder<HC> {

    final ConnectorContext context;
    public RestObjectClass objectClass;
    private Boolean emptyFilterSupported;
    Closure<?> implementationPrototype;

    public ScriptedGroovySearchBuilderImpl(ConnectorContext context, RestObjectClass objectClass) {
        this.context = context;
        this.objectClass = objectClass;
    }

    @Override
    public ScriptedGroovySearchBuilder emptyFilterSupported(boolean emptyFilterSupported) {
        this.emptyFilterSupported = emptyFilterSupported;
        return this;
    }

    @Override
    public ScriptedGroovySearchBuilder implementation(Closure<?> implementation) {
        this.implementationPrototype = implementation;
        return this;
    }

    @Override
    public boolean emptyFilterSupported() {
        return emptyFilterSupported;
    }

    @Override
    public FilterAwareExecuteQueryProcessor<HC> build() {
        return new ScriptedGroovySearchProcessor(this);
    }
}
