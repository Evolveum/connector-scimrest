/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy;

import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import com.evolveum.polygon.conndev.groovy.AbstractSearchOperationBuilder;

import com.evolveum.polygon.conndev.build.api.NormalizationBuilder;
import com.evolveum.polygon.conndev.build.api.SearchOperationBuilder;
import com.evolveum.polygon.conndev.build.api.SearchScriptBuilder;
import com.evolveum.polygon.conndev.spi.AttributeResolver;
import com.evolveum.polygon.scimrest.groovy.api.RestSearchOperationBuilder;
import com.evolveum.polygon.conndev.spi.AttributeResolvingSearchHandler;
import com.evolveum.polygon.conndev.spi.FilterBasedSearchDispatcher;
import com.evolveum.polygon.conndev.schema.BaseAttributeDefinition;
import com.evolveum.polygon.scimrest.impl.scim.ScimSearchHandler;
import com.evolveum.polygon.conndev.spi.ObjectSearchOperation;
import com.evolveum.polygon.conndev.spi.FilterAwareExecuteQueryProcessor;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class RestSearchOperationBuilderImpl extends AbstractSearchOperationBuilder implements RestSearchOperationBuilder, RestObjectOperationBuilder<ObjectSearchOperation> {

    private final BaseOperationSupportBuilder restParent;
    Map<String, EndpointBasedSearchBuilder<?,?>> endpointBuilder = new HashMap<>();
    private NormalizationBuilderImpl normalizationBuilder;
    private ScimSearchHandler.Builder scim;
    private DefinitionValue<Boolean> enabled = DefinitionValue.DEFAULT_TRUE;

    public RestSearchOperationBuilderImpl(BaseOperationSupportBuilder parent) {
        super(parent);
        this.restParent = parent;
    }

    @Override
    public boolean isEnabled() {
        return enabled.value();
    }

    @Override
    public SearchOperationBuilder enabled(DefinitionValue<Boolean> value) {
        enabled = enabled.moreSpecific(value);
        return this;
    }

    @Override
    public EndpointBasedSearchBuilder<?,?> endpoint(String path) {
        var builder = endpointBuilder.get(path);
        if (builder != null) {
            return builder;
        }
        builder = new EndpointBasedSearchBuilder<>(path, restParent.getObjectClass());
        endpointBuilder.put(path, builder);
        builders.add(builder);
        return builder;
    }

    @Override
    public EndpointBasedSearchBuilder<?,?> endpoint(String path, @DelegatesTo(value = EndpointBasedSearchBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> builder) {
        return GroovyClosures.callAndReturnDelegate(builder, endpoint(path));
    }

    @Override
    public SearchScriptBuilder custom() {
        var ret = new ScriptedGroovySearchBuilderImpl(restParent.context, restParent.getObjectClass());
        builders.add(ret);
        return ret;
    }

    @Override
    public NormalizationBuilder normalize() {
        if (normalizationBuilder == null) {
            normalizationBuilder = new NormalizationBuilderImpl();
        }
        return normalizationBuilder;
    }

    @Override
    public ObjectSearchOperation build() {
        if (builders.isEmpty() && scim == null) {
            // We don't have any endpoints, so we don't need to build anything, this results in search operation
            // being unsupported.
            return null;
        }

        return buildAttributeResolver(buildNormalizationHandler(buildFilterDispatcher()));
    }

    private ObjectSearchOperation buildNormalizationHandler(ObjectSearchOperation executeQueryProcessor) {
        if (normalizationBuilder == null) {
            return executeQueryProcessor;
        }
        return normalizationBuilder.build(executeQueryProcessor);
    }

    private ObjectSearchOperation buildFilterDispatcher() {
        var handlers = new HashSet<FilterAwareExecuteQueryProcessor>();
        ObjectSearchOperation emptyFilterHandler = null;
        ObjectSearchOperation anyFilterHandler = null;
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
        return new FilterBasedSearchDispatcher<>(emptyFilterHandler, anyFilterHandler,  handlers);
    }

    private ObjectSearchOperation buildAttributeResolver(ObjectSearchOperation dispatcher) {
        Set<AttributeResolver> perObjectResolvers = new HashSet<>();
        Set<AttributeResolver> batchedResolvers = new HashSet<>();
        Set<BaseAttributeDefinition> supportedAttributes = new HashSet<>();
        for (var builder : resolvers) {
            var resolver = builder.build();
            supportedAttributes.addAll(resolver.getSupportedAttributes());
            switch (builder.resolutionType()) {
                case BATCH -> batchedResolvers.add(resolver);
                case PER_OBJECT -> perObjectResolvers.add(resolver);
                default -> throw new IllegalStateException("Unknown resolver type: " + builder.resolutionType());
            }
        }
        for (var attribute : restParent.getObjectClass().attributes()) {
            if (attribute.emulated()) {
                var resolver = attribute.resolver();
                if (resolver == null && !supportedAttributes.contains(attribute)) {
                    throw new IllegalStateException("Attribute: " + attribute.remoteName() + " is emulated, but no resolver exists.");
                }
                switch (resolver.resolutionType()) {
                    case BATCH -> batchedResolvers.add(resolver);
                    case PER_OBJECT -> perObjectResolvers.add(resolver);
                    default -> throw new IllegalStateException("Unknown resolver type: " + resolver.resolutionType());
                }
            }
        }

        if (!perObjectResolvers.isEmpty() || !batchedResolvers.isEmpty()) {
            dispatcher = new AttributeResolvingSearchHandler(dispatcher, perObjectResolvers, batchedResolvers);
        }

        return dispatcher;
    }

    @Override
    public ScimSearchHandler.Builder scim() {
        if (this.scim == null) {
            this.scim = new ScimSearchHandler.Builder(restParent.getObjectClass());
        };
        return scim;
    }
}
