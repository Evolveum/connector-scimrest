package com.evolveum.polygon.scim.rest.groovy;

import groovy.lang.Closure;

public class ScriptedGroovySearchBuilderImpl implements ScriptedGroovySearchBuilder {

    private Boolean emptyFilterSupported;
    private Closure<?> implementationPrototype;

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
}
