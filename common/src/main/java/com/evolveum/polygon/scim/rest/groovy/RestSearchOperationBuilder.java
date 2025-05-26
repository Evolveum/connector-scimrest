package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.groovy.api.AttributeResolverBuilder;
import com.evolveum.polygon.scim.rest.groovy.api.SearchScriptBuilder;
import com.evolveum.polygon.scim.rest.groovy.api.SearchOperationBuilder;
import com.evolveum.polygon.scim.rest.spi.ExecuteQueryProcessor;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class RestSearchOperationBuilder<HC> implements ObjectClassOperationBuilder<ExecuteQueryProcessor<HC>>, SearchOperationBuilder {

    private final BaseOperationSupportBuilder<HC> parent;
    Map<String, EndpointBasedSearchBuilder<HC,?,?>> endpointBuilder = new HashMap<>();
    Set<FilterAwareSearchProcessorBuilder<HC>> builders = new HashSet<>();
    Set<AttributeResolverBuilder> resolvers = new HashSet<>();

    public RestSearchOperationBuilder(BaseOperationSupportBuilder<HC> parent) {
        this.parent = parent;
    }

    @Override
    public EndpointBasedSearchBuilder<HC,?,?> endpoint(String path) {
        var builder = endpointBuilder.get(path);
        if (builder != null) {
            return builder;
        }
        builder = new EndpointBasedSearchBuilder<>(path, parent.getObjectClass());
        endpointBuilder.put(path, builder);
        builders.add(builder);
        return builder;
    }

    @Override
    public EndpointBasedSearchBuilder<HC,?,?> endpoint(String path, @DelegatesTo(value = EndpointBasedSearchBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> builder) {
        return GroovyClosures.callAndReturnDelegate(builder, endpoint(path));
    }


    @Override
    public ScriptedAttributeResolverBuilder<HC> attributeResolver(@DelegatesTo(value = AttributeResolverBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> definition) {
        var ret = new ScriptedAttributeResolverBuilder<HC>(parent.context, parent.getObjectClass());
        resolvers.add(ret);
        return GroovyClosures.callAndReturnDelegate(definition, ret);
    }

    @Override
    public SearchScriptBuilder custom(@DelegatesTo(SearchScriptBuilder.class) Closure<?> definition) {
        var ret = new ScriptedGroovySearchBuilderImpl(parent.context, parent.getObjectClass());
        builders.add(ret);
        return GroovyClosures.callAndReturnDelegate(definition, ret);
    }

    public ExecuteQueryProcessor<HC> build() {
        if (endpointBuilder.isEmpty()) {
            // We don't have any endpoints, so we don't need to build anything, this results in search operation
            // being unsupported.
            return null;
        }

        var handlers = new HashSet<FilterAwareExecuteQueryProcessor<HC>>();
        ExecuteQueryProcessor<HC> emptyFilterHandler = null;
        for (var builder : builders) {
            var handler =  builder.build();
            handlers.add(handler);
            if (builder.emptyFilterSupported()) {
                if (emptyFilterHandler == null) {
                    emptyFilterHandler = handler;
                } else {
                    // FIXME: Throw better exception
                    throw new IllegalStateException("Multiple default endpoints are not supported");
                }
            }
        }
        return new FilterBasedSearchDispatcher<>(emptyFilterHandler, handlers);
    }
}
