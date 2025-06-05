package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.groovy.api.FilterSpecification;
import com.evolveum.polygon.scim.rest.groovy.api.SearchScriptBuilder;
import com.evolveum.polygon.scim.rest.schema.MappedObjectClass;
import groovy.lang.Closure;

import java.util.HashSet;
import java.util.Set;

public class ScriptedGroovySearchBuilderImpl<HC> implements SearchScriptBuilder, FilterAwareSearchProcessorBuilder<HC> {

    final ConnectorContext context;
    public MappedObjectClass objectClass;
    public Set<FilterSpecification> supportedFilters = new HashSet<>();
    private Boolean emptyFilterSupported;
    Closure<?> implementationPrototype;

    public ScriptedGroovySearchBuilderImpl(ConnectorContext context, MappedObjectClass objectClass) {
        this.context = context;
        this.objectClass = objectClass;
    }

    @Override
    public SearchScriptBuilder emptyFilterSupported(boolean emptyFilterSupported) {
        this.emptyFilterSupported = emptyFilterSupported;
        return this;
    }

    @Override
    public SearchScriptBuilder implementation(Closure<?> implementation) {
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
