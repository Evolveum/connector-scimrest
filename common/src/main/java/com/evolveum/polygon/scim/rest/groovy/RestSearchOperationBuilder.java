package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.spi.ExecuteQueryProcessor;
import com.evolveum.polygon.scim.rest.spi.SearchEndpointHandler;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class RestSearchOperationBuilder<HC> implements ObjectClassOperationBuilder<ExecuteQueryProcessor<HC>> {

    private final BaseOperationSupportBuilder<HC> parent;
    Map<String, EndpointBasedSearchBuilder<?,?>> endpointBuilder = new HashMap<>();

    public RestSearchOperationBuilder(BaseOperationSupportBuilder<HC> parent) {
        this.parent = parent;
    }


    public EndpointBasedSearchBuilder<?,?> endpoint(String path) {
        return endpointBuilder.computeIfAbsent(path, k -> new EndpointBasedSearchBuilder<>(k, parent.getObjectClass()));
    }

    public EndpointBasedSearchBuilder<?,?> endpoint(String path, @DelegatesTo(EndpointBasedSearchBuilder.class) Closure<?> builder) {
        return GroovyClosures.callAndReturnDelegate(builder, endpoint(path));
    }

    public ScriptedGroovySearchBuilder custom(@DelegatesTo(ScriptedGroovySearchBuilder.class) Closure<?> definition) {
        var ret = new ScriptedGroovySearchBuilderImpl();
        return GroovyClosures.callAndReturnDelegate(definition, ret);
    }

    public ExecuteQueryProcessor<HC> build() {
        if (endpointBuilder.isEmpty()) {
            // We don't have any endpoints, so we don't need to build anything, this results in search operation
            // being unsupported.
            return null;
        }

        var endpoints = new HashSet<SearchEndpointHandler<?,?>>();
        SearchEndpointHandler<?,?> defaultEndpoint = null;
        for (var endpointBuilder : endpointBuilder.values()) {
            var handler =  endpointBuilder.build();
            endpoints.add(handler);
            if (endpointBuilder.emptyFilterSupported) {
                if (defaultEndpoint == null) {
                    defaultEndpoint = handler;
                } else {
                    throw new IllegalStateException("Multiple default endpoints are not supported");
                }
            }
        }
        var filterProcessor = new MultipleEndpointSearchProcessor<HC>(defaultEndpoint, endpoints);
        // FIXME: Add type safety contracts
        return (ExecuteQueryProcessor<HC>) new RequestProcessorBasedSearch(parent.getObjectClass(),filterProcessor);
    }
}
