/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy.operation;

import com.evolveum.polygon.scimrest.groovy.search.EndpointBasedSearchBuilder;
import com.evolveum.polygon.scimrest.groovy.schema.BaseOperationSupportBuilder;

import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import com.evolveum.polygon.conndev.groovy.AbstractSearchOperationBuilder;
import com.evolveum.polygon.conndev.groovy.GroovySearchScriptBuilder;

import com.evolveum.polygon.conndev.build.api.SearchScriptBuilder;
import com.evolveum.polygon.scimrest.groovy.api.RestSearchOperationBuilder;
import com.evolveum.polygon.conndev.spi.FilterBasedSearchDispatcher;
import com.evolveum.polygon.scimrest.impl.scim.ScimSearchHandler;
import com.evolveum.polygon.conndev.spi.ObjectSearchOperation;
import com.evolveum.polygon.conndev.spi.FilterAwareExecuteQueryProcessor;
import com.evolveum.polygon.scimrest.schema.RestObjectClassDefinition;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class RestSearchOperationBuilderImpl extends AbstractSearchOperationBuilder<RestObjectClassDefinition> implements RestSearchOperationBuilder {

    Map<String, EndpointBasedSearchBuilder<?,?>> endpointBuilder = new HashMap<>();
    private ScimSearchHandler.Builder scim;

    public RestSearchOperationBuilderImpl(BaseOperationSupportBuilder parent) {
        super(parent);
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
}
