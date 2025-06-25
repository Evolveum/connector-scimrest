/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.ContextLookup;
import com.evolveum.polygon.scim.rest.groovy.api.FilterSpecification;
import com.evolveum.polygon.scim.rest.spi.ExecuteQueryProcessor;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.Filter;

import java.util.HashSet;
import java.util.Set;

public class FilterBasedSearchDispatcher<HC> implements ExecuteQueryProcessor {

    private final static FilterSpecification.Attribute IS_UID_FILTER_WITH_SINGLE_VALUE = FilterSpecification.attribute(Uid.NAME).eq().anySingleValue();
    private final ExecuteQueryProcessor anyFilterHandler;


    ExecuteQueryProcessor emptyFilterSupport;
    Set<FilterAwareExecuteQueryProcessor> implementations;

    public FilterBasedSearchDispatcher(ExecuteQueryProcessor emptyFilterHandler, ExecuteQueryProcessor anyFilterHandler, HashSet<FilterAwareExecuteQueryProcessor> handlers) {
        this.emptyFilterSupport = emptyFilterHandler;
        this.anyFilterHandler = anyFilterHandler;
        this.implementations = Set.copyOf(handlers);
    }

    @Override
    public void executeQuery(ContextLookup context, Filter filter, ResultsHandler resultsHandler, OperationOptions operationOptions) {

        var rewrittenFilter = filter;
        var implementation = findImplementation(filter, operationOptions);
        if (implementation == null && IS_UID_FILTER_WITH_SINGLE_VALUE.matches(rewrittenFilter)) {
            // Let's try with name filter
            EqualsFilter eqFilter = (EqualsFilter) rewrittenFilter;
            if (eqFilter.getAttribute() instanceof Uid uid) {
                var nameHint = uid.getNameHint();
                if (nameHint != null) {
                    // If we have name hint we can rewrite filter
                    rewrittenFilter = new EqualsFilter(nameHint);
                    implementation = findImplementation(rewrittenFilter, operationOptions);
                }
            }
        }
        if (implementation == null) {
            throw new IllegalArgumentException("Unsupported filter " + filter);
        }
        implementation.executeQuery(context, rewrittenFilter, resultsHandler, operationOptions);
    }

    private ExecuteQueryProcessor findImplementation(Filter filter, OperationOptions operationOptions) {
        if (isEmpty(filter) && emptyFilterSupport != null) {
            return emptyFilterSupport;
        }

        for (var impl : implementations) {
            if (impl.supports(filter, operationOptions)) {
                return impl;
            }
        }
        return anyFilterHandler;
    }

    private boolean isEmpty(Filter filter) {
        return filter == null;
    }
}
