/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy;

import com.evolveum.polygon.scimrest.ContextLookup;
import com.evolveum.polygon.scimrest.groovy.api.FilterSpecification;
import com.evolveum.polygon.scimrest.schema.MappedObjectClass;
import groovy.lang.Closure;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.filter.Filter;

import java.util.Set;

public class ScriptedGroovySearchProcessor implements FilterAwareExecuteQueryProcessor {



    private final MappedObjectClass objectClass;
    private final Closure<?> implementation;
    private final Set<FilterSpecification> supportedFilters;
    private final ConnectorContext context;

    public ScriptedGroovySearchProcessor(ScriptedGroovySearchBuilderImpl builder) {
        this.context = builder.context;
        this.objectClass = builder.objectClass;
        this.implementation = builder.implementationPrototype;
        this.supportedFilters = builder.supportedFilters;
    }

    @Override
    public void executeQuery(ContextLookup context, Filter filter, ResultsHandler resultsHandler, OperationOptions operationOptions) {
        var scriptContext = new SearchScriptContextImpl(this.context, objectClass,filter, resultsHandler, operationOptions);
        GroovyClosures.copyAndCall(implementation, scriptContext);
    }

    @Override
    public boolean supports(Filter filter) {
        return supportedFilters.stream().anyMatch(filterSpecification -> filterSpecification.matches(filter));
    }
}
