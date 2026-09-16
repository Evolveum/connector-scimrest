/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.scim;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.build.api.SearchHandlerBuilder;
import com.evolveum.polygon.scimrest.groovy.api.scim.ScimSearchBuilder;
import com.evolveum.polygon.conndev.spi.BatchAwareResultHandler;
import com.evolveum.polygon.conndev.spi.FilterAwareExecuteQueryProcessor;
import com.evolveum.polygon.conndev.groovy.FilterAwareSearchProcessorBuilder;
import com.evolveum.polygon.conndev.api.FilterSpecification;
import com.evolveum.polygon.scimrest.impl.rest.HttpExceptionMapper;
import com.evolveum.polygon.scimrest.impl.rest.HttpStatusMapper;
import com.evolveum.polygon.scimrest.schema.RestObjectClassDefinition;
import com.unboundid.scim2.common.GenericScimResource;
import groovy.lang.Closure;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.exceptions.ConnectorException;

import java.util.HashSet;
import java.util.Set;
public class ScimSearchHandler implements FilterAwareExecuteQueryProcessor {

    private final RestObjectClassDefinition objectClass;
    private final Set<FilterSpecification> supportedFilters;
    private final ScimFilterTranslator filterTranslator;

    private final boolean supportsEmptyFilter;
    private final boolean supportsAnyFilter;

    public ScimSearchHandler(RestObjectClassDefinition objectClass, boolean emptySupported, boolean anyFilterSupported, Set<FilterSpecification> supportedFilters) {
        this.objectClass = objectClass;
        this.supportsEmptyFilter = emptySupported;
        this.supportsAnyFilter = anyFilterSupported;
        this.supportedFilters = supportedFilters;
        this.filterTranslator = ScimFilterTranslator.fromObjectClass(objectClass);
    }

    public void performSearch(ScimContext context, Filter query, ResultsHandler handler, OperationOptions options) {
        var shouldContinue = true;
        var currentPage = 1;
        var pageLimit = 25; // FIXME: Make this configurable from builders.
        var totalProcessed = 0;

        var scim = context.scimClient();
        var resource = context.resourceForObjectClass(objectClass.objectClass());
        var scimFilter = query == null ? null : filterTranslator.translate(query);
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
        } catch (ScimHttpErrorException e) {
            // HTTP error (status + RFC 7644 detail) — translate per operation kind: 404 on the
            // search endpoint is a misconfiguration, 5xx is transient, 401/403 are credential
            // errors. The server detail is carried into the resulting message.
            throw ScimExceptionMapper.map(e, HttpStatusMapper.OperationKind.SEARCH, null);
        } catch (ConnectorException e) {
            // ICF type was already set at the boundary (e.g. mapped network failure) — never re-wrap.
            throw e;
        } catch (Exception e) {
            throw new ConnectorException(
                    "SCIM search failed at " + resource.relativeEndpoint() + ", page " + currentPage
                            + ": " + HttpExceptionMapper.causeMessage(e), e);
        }
    }

    private ConnectorObject deserializeFromRemote(GenericScimResource remoteObj) {
        var builder = objectClass.newObjectBuilder();
        for (var attributeDef : objectClass.attributes()) {
            var mapping = attributeDef.scim();
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
        if (filter == null) {
            return supportsEmptyFilter;
        }
        if (!filterTranslator.isTranslatable(filter)) {
            return false;
        }
        if (supportsAnyFilter) {
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
        } catch (ScimHttpErrorException e) {
            // HTTP 404 means the object was deleted on the resource: midPoint must see
            // UnknownUidException to tombstone the shadow and fire the discovery DELETE.
            throw ScimExceptionMapper.map(e, HttpStatusMapper.OperationKind.GET, uid.toString());
        } catch (ConnectorException e) {
            // ICF type was already set at the boundary (e.g. mapped network failure) — never re-wrap.
            throw e;
        } catch (Exception e) {
            throw new ConnectorException(
                    "SCIM retrieve of object with UID " + uid + " failed at " + resource.relativeEndpoint()
                            + ": " + HttpExceptionMapper.causeMessage(e), e);
        }
    }

    public static class Builder implements ScimSearchBuilder, FilterAwareSearchProcessorBuilder {

        private boolean enabled = true;
        private boolean emptySupported;
        private boolean anyFilterSupported  = true;
        private final Set<FilterSpecification> supportedFilters = new HashSet<>();
        private final RestObjectClassDefinition objectClass;
        private Limitations limitations;

        public Builder(RestObjectClassDefinition objectClass) {
            this.objectClass = objectClass;
        }

        /**
         * Limitations of the SCIM search delegate every setting back onto the enclosing
         * {@link Builder} - {@code scim { limitations { emptyFilterSupported true } } } and
         * {@code scim { emptyFilterSupported true } } configure the same state.
         */
        private class LimitationsImpl implements Limitations {

            @Override
            public Limitations emptyFilterSupported(boolean emptyFilterSupported) {
                Builder.this.emptyFilterSupported(emptyFilterSupported);
                return this;
            }

            @Override
            public FilterSpecification.Attribute attribute(String name) {
                var connId = objectClass.attributeFromProtocolName(name).connId();
                if (connId != null) {
                    return FilterSpecification.attribute(connId.getName());
                }
                return FilterSpecification.attribute(name);
            }

            @Override
            public Limitations supportedFilter(FilterSpecification filterSpec) {
                Builder.this.supportedFilters(filterSpec);
                return this;
            }

            /**
             * Declares the filter as supported. Unlike REST endpoints, SCIM search has no
             * per-filter mapping hook - filters are translated to SCIM filter expressions
             * automatically - so the closure carries no mapping behavior. It is evaluated
             * against the (currently empty) {@link SearchHandlerBuilder.FilterSupportImplementation}
             * delegate purely so that declarative {@code supportedFilter(spec) { ... } }
             * blocks are accepted; the specification itself is what limits which filters
             * the search handler - and thus the {@code FilterBasedSearchDispatcher} - applies.
             */
            @Override
            public Limitations supportedFilter(FilterSpecification filterSpec, Closure<?> closure) {
                var delegate = new SearchHandlerBuilder.FilterSupportImplementation() {
                };
                closure.setDelegate(delegate);
                closure.setResolveStrategy(Closure.DELEGATE_ONLY);
                closure.call();
                Builder.this.supportedFilters(filterSpec);
                return this;
            }
        }

        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        @Override
        public boolean emptyFilterSupported() {
            return emptySupported;
        }

        public Builder emptyFilterSupported(boolean emptySupported) {
            this.emptySupported = emptySupported;
            return this;
        }

        public Builder anyFilterSupported(boolean anyFilterSupported) {
            this.anyFilterSupported = anyFilterSupported;
            return this;
        }

        public Builder supportedFilters(FilterSpecification filterSpecification) {
            supportedFilters.add(filterSpecification);
            this.anyFilterSupported = false;
            return this;
        }

        @Override
        public Limitations limitations() {
            if (limitations == null) {
                limitations = new LimitationsImpl();
            }
            return limitations;
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
