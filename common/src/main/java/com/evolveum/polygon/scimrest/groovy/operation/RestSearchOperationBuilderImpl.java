/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy.operation;

import com.evolveum.polygon.scimrest.groovy.search.EndpointBasedSearchBuilder;
import com.evolveum.polygon.conndev.spi.AttributeResolver;
import com.evolveum.polygon.scimrest.groovy.api.RelationshipReadBuilder;
import com.evolveum.polygon.scimrest.config.RelationshipContext;
import com.evolveum.polygon.scimrest.schema.RestAttributeDefinition;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import com.evolveum.polygon.scimrest.groovy.schema.BaseOperationSupportBuilder;

import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import com.evolveum.polygon.conndev.groovy.AbstractSearchOperationBuilder;
import com.evolveum.polygon.conndev.groovy.FilterAwareSearchProcessorBuilder;
import com.evolveum.polygon.conndev.groovy.GroovySearchScriptBuilder;

import com.evolveum.polygon.conndev.build.api.SearchScriptBuilder;
import com.evolveum.polygon.scimrest.groovy.api.RestSearchOperationBuilder;
import com.evolveum.polygon.conndev.spi.FilterBasedSearchDispatcher;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import com.evolveum.polygon.scimrest.impl.scim.ScimSearchHandler;
import com.evolveum.polygon.conndev.spi.ObjectSearchOperation;
import com.evolveum.polygon.conndev.spi.FilterAwareExecuteQueryProcessor;
import com.evolveum.polygon.scimrest.schema.RestObjectClassDefinition;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;

public class RestSearchOperationBuilderImpl extends AbstractSearchOperationBuilder<RestObjectClassDefinition> implements RestSearchOperationBuilder {

    Map<String, EndpointBasedSearchBuilder<?,?>> endpointBuilder = new HashMap<>();
    private ScimSearchHandler.Builder scim;
    private final Map<String, RecordSearchBuilder> relationships = new LinkedHashMap<>();

    public RestSearchOperationBuilderImpl(BaseOperationSupportBuilder parent) {
        super(parent);
    }

    @Override
    public RelationshipReadBuilder relationshipRecords(String attribute) {
        RestAttributeDefinition definition;
        try {
            definition = parent.getObjectClass().requireAttribute(attribute, "when defining relationship record retrieval");
        } catch (ConfigurationException exception) {
            throw new RelationshipContext(attribute, null).invalid(exception.getMessage(), exception);
        }
        var name = definition.connId().getName();
        if (relationships.containsKey(name)) {
            throw relationships.get(name).configuration().invalid("Multiple relationship record declarations resolve to attribute '" + name + "'");
        }
        var builder = new RecordSearchBuilder(definition);
        relationships.put(name, builder);
        return builder;
    }

    @Override
    protected void applyAdditionalAttributeResolvers(Set<AttributeResolver> perObject, Set<AttributeResolver> batched) {
        for (var relationship : relationships.values()) {
            for (var resolver : resolvers) {
                var configured = resolver.build();
                if (configured != null && configured.getSupportedAttributes().stream()
                        .anyMatch(attribute -> relationship.attributeName().equals(attribute.connId().getName()))) {
                    throw relationship.configuration().invalid("Relationship attribute '" + relationship.attributeName() + "' already has an attribute resolver");
                }
            }
            perObject.add(relationship.build());
        }
    }

    @Override
    public ObjectSearchOperation build() {
        if (!relationships.isEmpty() && isEmpty()) {
            throw relationships.values().iterator().next().configuration().invalid("Relationship record retrieval requires an object search endpoint");
        }
        var operation = super.build();
        if (relationships.isEmpty()) {
            return operation;
        }
        return (context, filter, handler, options) -> operation.executeQuery(context, filter, handler,
                options != null ? options : new OperationOptionsBuilder().build());
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
    public SearchScriptBuilder custom() {
        var ret = new GroovySearchScriptBuilder(parent.context, parent.getObjectClass());
        builders.add(ret);
        return ret;
    }

    @Override
    protected boolean isEmpty() {
        return builders.isEmpty() && scim == null;
    }

    @Override
    protected ObjectSearchOperation buildFilterDispatcher() {
        var handlers = new HashSet<FilterAwareExecuteQueryProcessor>();
        ObjectSearchOperation emptyFilterHandler = null;
        ObjectSearchOperation anyFilterHandler = null;
        String defaultEndpoint = null;
        for (var builder : builders) {
            if (builder.isEnabled()) {
                var handler = builder.build();
                handlers.add(handler);
                if (builder.emptyFilterSupported()) {
                    if (emptyFilterHandler == null) {
                        emptyFilterHandler = handler;
                        defaultEndpoint = endpointPath(builder);
                    } else {
                        throw new ConfigurationException(
                                "Multiple search endpoints declare empty-filter support: "
                                        + defaultEndpoint + " and " + endpointPath(builder)
                                        + " — only one endpoint may handle the empty filter");
                    }
                }
            }
        }
        if (scim != null && scim.isEnabled()) {
            var handler = scim.build();
            // The SCIM handler also takes part in the per-filter dispatch, so its
            // supports() check (filter translatability and declared limitations)
            // is consulted like any other handler's.
            handlers.add(handler);

            if (emptyFilterHandler == null && scim.emptyFilterSupported()) {
                emptyFilterHandler = handler;
            }
            if (anyFilterHandler == null && scim.anyFilterSupported()) {
                anyFilterHandler = handler;
            }
        }
        return new FilterBasedSearchDispatcher<>(emptyFilterHandler, anyFilterHandler,  handlers);
    }

    @Override
    public ScimSearchHandler.Builder scim() {
        if (this.scim == null) {
            this.scim = new ScimSearchHandler.Builder(parent.getObjectClass());
        }
        return scim;
    }

    private String endpointPath(FilterAwareSearchProcessorBuilder builder) {
        return endpointBuilder.entrySet().stream()
                .filter(entry -> entry.getValue() == builder)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("?");
    }
}
