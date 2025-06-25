/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.scim;

import com.evolveum.polygon.scimrest.ContextLookup;
import com.evolveum.polygon.scimrest.spi.BatchAwareResultHandler;
import com.evolveum.polygon.scimrest.spi.FilterAwareExecuteQueryProcessor;
import com.evolveum.polygon.scimrest.groovy.FilterAwareSearchProcessorBuilder;
import com.evolveum.polygon.scimrest.groovy.api.FilterSpecification;
import com.evolveum.polygon.scimrest.groovy.api.ScimSearchBuilder;
import com.evolveum.polygon.scimrest.schema.MappedObjectClass;
import com.evolveum.polygon.scimrest.schema.ScimAttributeMapping;
import com.unboundid.scim2.common.GenericScimResource;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.filter.Filter;

import java.util.HashSet;
import java.util.Set;

// FIXME: Consider making this JSON agnostic and format / parsing handling will be injected.
public class ScimSearchHandler implements FilterAwareExecuteQueryProcessor {

    private final MappedObjectClass objectClass;
    private final Set<FilterSpecification> supportedFilters;

    private final boolean supportsEmptyFilter;
    private final boolean supportsAnyFilter;

    public ScimSearchHandler(MappedObjectClass objectClass, boolean emptySupported, boolean anyFilterSupported, Set<FilterSpecification> supportedFilters) {
        this.objectClass = objectClass;
        this.supportsEmptyFilter = emptySupported;
        this.supportsAnyFilter = anyFilterSupported;
        this.supportedFilters = supportedFilters;
    }

    public void performSearch(ScimContext context, Filter query, ResultsHandler handler, OperationOptions options) {
        var shouldContinue = true;
        var currentPage = 1;
        var pageLimit = 25; // FIXME: Make this configurable from builders.
        var totalProcessed = 0;

        var scim = context.scimClient();
        var resource = context.resourceForObjectClass(objectClass.objectClass());
        var scimFilter = translate(query);
        try {
            do {
                var batchProcessed = 0;

                var response = scim.searchRequest(resource.relativeEndpoint())
                        .filter(scimFilter)
                        .page(currentPage,pageLimit)
                        .invoke(GenericScimResource.class);

                var remoteObjs = response.getResources();

                for (var remoteObj : remoteObjs) {
                    ConnectorObject obj = deserializeFromRemote(remoteObj);
                    handler.handle(obj);
                    batchProcessed++;
                }
                BatchAwareResultHandler.batchFinished(handler);
                totalProcessed += batchProcessed;
                // TODO: Add support for cursor-based continuation https://developer.zendesk.com/api-reference/introduction/pagination/#using-offset-pagination
                // TODO: Maybe paging and cursor API could be merged to being two different implentations of cursor
                if (batchProcessed == 0) {
                    shouldContinue = false;
                }
                var totalCount = response.getTotalResults();
                if (totalCount >= 0 && totalProcessed >= totalCount) {
                    shouldContinue = false;
                }
                currentPage++;
            } while (shouldContinue);
        } catch (Exception e) {
            // FIXME: Add proper error handling here and maybe translation to ConnID exceptions
            throw new RuntimeException(e);
        }
    }

    private String translate(Filter query) {
        return null;

    }

    private ConnectorObject deserializeFromRemote(GenericScimResource remoteObj) {
        var builder = objectClass.newObjectBuilder();
        for (var attributeDef : objectClass.attributes()) {
            var mapping = attributeDef.mapping(ScimAttributeMapping.class);
            if (mapping != null) {
                var value = mapping.valuesFromObject(remoteObj.getObjectNode());
                if (value != null) {
                    builder.addAttribute(attributeDef.attributeOf(value));
                }
            }
        }
        return builder.build();

    }

    @Override
    public boolean supports(Filter filter) {
        if ((supportsEmptyFilter && filter == null) || supportsAnyFilter) {
            return true;
        }
        return supportedFilters.stream().anyMatch(a -> a.matches(filter));
    }

    @Override
    public void executeQuery(ContextLookup context, Filter filter, ResultsHandler resultsHandler, OperationOptions operationOptions) {
        var uid = FilterSpecification.UID_EQUALS_SINGLE_VALUE.checkOnlyValue(filter);
        if (uid != null) {
            performGet(context.get(ScimContext.class), uid, resultsHandler, operationOptions);
        } else {
            performSearch(context.get(ScimContext.class), filter, resultsHandler, operationOptions);
        }
    }

    private void performGet(ScimContext context, Object uid, ResultsHandler handler, OperationOptions operationOptions) {
        var scim = context.scimClient();
        var resource = context.resourceForObjectClass(objectClass.objectClass());

        try {
            var remoteObj = scim.retrieveRequest(resource.relativeEndpoint(), uid.toString())
                    .invoke(GenericScimResource.class);
            ConnectorObject obj = deserializeFromRemote(remoteObj);
            handler.handle(obj);
            BatchAwareResultHandler.batchFinished(handler);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static class Builder implements ScimSearchBuilder, FilterAwareSearchProcessorBuilder {

        private boolean enabled = true;
        private boolean emptySupported;
        private boolean anyFilterSupported  = true;
        private final Set<FilterSpecification> supportedFilters = new HashSet<>();
        private final MappedObjectClass objectClass;

        public Builder(MappedObjectClass objectClass) {
            this.objectClass = objectClass;
        }

        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public ScimSearchBuilder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        @Override
        public boolean emptyFilterSupported() {
            return emptySupported;
        }

        public ScimSearchBuilder emptyFilterSupported(boolean emptySupported) {
            this.emptySupported = emptySupported;
            return this;
        }

        public ScimSearchBuilder anyFilterSupported(boolean anyFilterSupported) {
            this.anyFilterSupported = anyFilterSupported;
            return this;
        }

        public ScimSearchBuilder supportedFilters(FilterSpecification filterSpecification) {
            supportedFilters.add(filterSpecification);
            this.anyFilterSupported = false;
            return this;
        }

        @Override
        public boolean anyFilterSupported() {
            return anyFilterSupported;
        }

        @Override
        public FilterAwareExecuteQueryProcessor build() {
            return new ScimSearchHandler(objectClass, emptySupported, anyFilterSupported, supportedFilters);
        }
    }
}
