package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.groovy.api.*;
import com.evolveum.polygon.scim.rest.schema.MappedObjectClass;
import com.evolveum.polygon.scim.rest.spi.ExecuteQueryProcessor;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

import java.util.*;

public class RestSearchOperationBuilder implements ObjectClassOperationBuilder<ExecuteQueryProcessor>, SearchOperationBuilder {

    private final BaseOperationSupportBuilder parent;
    Map<String, EndpointBasedSearchBuilder<?,?>> endpointBuilder = new HashMap<>();
    Set<FilterAwareSearchProcessorBuilder> builders = new HashSet<>();
    Set<ScriptedAttributeResolverBuilder> resolvers = new HashSet<>();
    private ScimSearchHandler.Builder scim;

    public RestSearchOperationBuilder(BaseOperationSupportBuilder parent) {
        this.parent = parent;
    }

    @Override
    public EndpointBasedSearchBuilder<?,?> endpoint(String path) {
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
    public EndpointBasedSearchBuilder<?,?> endpoint(String path, @DelegatesTo(value = EndpointBasedSearchBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> builder) {
        return GroovyClosures.callAndReturnDelegate(builder, endpoint(path));
    }


    @Override
    public ScriptedAttributeResolverBuilder attributeResolver(@DelegatesTo(value = AttributeResolverBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> definition) {
        var ret = new ScriptedAttributeResolverBuilder(parent.context, parent.getObjectClass());
        resolvers.add(ret);
        return GroovyClosures.callAndReturnDelegate(definition, ret);
    }

    @Override
    public SearchScriptBuilder custom(@DelegatesTo(SearchScriptBuilder.class) Closure<?> definition) {
        var ret = new ScriptedGroovySearchBuilderImpl(parent.context, parent.getObjectClass());
        builders.add(ret);
        return GroovyClosures.callAndReturnDelegate(definition, ret);
    }

    public ExecuteQueryProcessor build() {
        if (endpointBuilder.isEmpty() && scim == null) {
            // We don't have any endpoints, so we don't need to build anything, this results in search operation
            // being unsupported.
            return null;
        }

        var handlers = new HashSet<FilterAwareExecuteQueryProcessor>();
        ExecuteQueryProcessor emptyFilterHandler = null;
        ExecuteQueryProcessor anyFilterHandler = null;
        for (var builder : builders) {
            if (builder.isEnabled()) {
                var handler = builder.build();
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
        }
        if (scim != null && scim.isEnabled()) {
            var handler = scim.build();

            if (emptyFilterHandler == null && scim.emptyFilterSupported()) {
                emptyFilterHandler = handler;
            }
            if (anyFilterHandler == null && scim.anyFilterSupported()) {
                anyFilterHandler = handler;
            }
        }

        ExecuteQueryProcessor dispatcher = new FilterBasedSearchDispatcher<>(emptyFilterHandler, anyFilterHandler,  handlers);
        if (!resolvers.isEmpty()) {
            Set<AttributeResolver> perObjectResolvers = new HashSet<>();
            Set<AttributeResolver> batchedResolvers = new HashSet<>();
            for (var builder : resolvers) {
                var resolver = builder.build();
                switch (builder.resolutionType()) {
                    case BATCH -> batchedResolvers.add(resolver);
                    case PER_OBJECT -> perObjectResolvers.add(resolver);
                    default -> throw new IllegalStateException("Unknown resolver type: " + builder.resolutionType());
                }
            }
            dispatcher = new AttributeResolvingSearchHandler(dispatcher, perObjectResolvers, batchedResolvers);


        }

        return dispatcher;
    }

    @Override
    public ScimSearchHandler.Builder scim() {
        if (this.scim == null) {
            this.scim = new ScimSearchHandler.Builder(parent.getObjectClass());
        };
        return scim;
    }
}
