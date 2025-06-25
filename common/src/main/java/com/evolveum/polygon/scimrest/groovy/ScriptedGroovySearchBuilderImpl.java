/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy;

import com.evolveum.polygon.scimrest.groovy.api.FilterSpecification;
import com.evolveum.polygon.scimrest.groovy.api.SearchScriptBuilder;
import com.evolveum.polygon.scimrest.schema.MappedObjectClass;
import com.evolveum.polygon.scimrest.spi.FilterAwareExecuteQueryProcessor;
import groovy.lang.Closure;

import java.util.HashSet;
import java.util.Set;

public class ScriptedGroovySearchBuilderImpl implements SearchScriptBuilder, FilterAwareSearchProcessorBuilder {

    final ConnectorContext context;
    public MappedObjectClass objectClass;
    public Set<FilterSpecification> supportedFilters = new HashSet<>();
    private Boolean emptyFilterSupported;
    Closure<?> implementationPrototype;
    private boolean enabled = true;

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
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public FilterAwareExecuteQueryProcessor build() {
        return new ScriptedGroovySearchProcessor(this);
    }
}
