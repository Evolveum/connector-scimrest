package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.schema.RestObjectClass;
import groovy.lang.Closure;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.filter.Filter;

import java.util.HashSet;
import java.util.Set;

public class ScriptedGroovySearchProcessor<HC> implements FilterAwareExecuteQueryProcessor<HC> {



    private final RestObjectClass objectClass;
    private final Closure<?> implementation;
    private final Set<FilterSpecification> supportedFilters;
    private final ConnectorContext context;

    public <HC> ScriptedGroovySearchProcessor(ScriptedGroovySearchBuilderImpl<HC> builder) {
        this.context = builder.context;
        this.objectClass = builder.objectClass;
        this.implementation = builder.implementationPrototype;
        this.supportedFilters = new HashSet<>();
    }

    @Override
    public void executeQuery(HC context, Filter filter, ResultsHandler resultsHandler, OperationOptions operationOptions) {
        var scriptContext = new SearchScriptContextImpl(this.context, objectClass,filter, resultsHandler, operationOptions);
        GroovyClosures.copyAndCall(implementation, scriptContext);
    }

    @Override
    public boolean supports(Filter filter) {
        return supportedFilters.stream().anyMatch(filterSpecification -> filterSpecification.matches(filter));
    }
}
