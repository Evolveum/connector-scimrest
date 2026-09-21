/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy.search;

import com.evolveum.polygon.scimrest.groovy.endpoint.ResponseObjectExtractor;
import com.evolveum.polygon.scimrest.groovy.endpoint.QueryRequestBuilderImpl;

import com.evolveum.polygon.conndev.annotations.Script;
import com.evolveum.polygon.conndev.api.FilterSpecification;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import com.evolveum.polygon.conndev.groovy.FilterAwareSearchProcessorBuilder;

import com.evolveum.polygon.scimrest.api.HttpRequestSpecification;
import com.evolveum.polygon.scimrest.groovy.api.HttpMethod;
import com.evolveum.polygon.scimrest.groovy.api.PagingInfo;
import com.evolveum.polygon.scimrest.groovy.api.ResponseWrapper;
import com.evolveum.polygon.scimrest.groovy.api.RestSearchEndpointBuilder;
import com.evolveum.polygon.scimrest.schema.RestObjectClassDefinition;
import com.evolveum.polygon.scimrest.spi.TotalCountExtractor;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.identityconnectors.framework.common.objects.filter.AttributeFilter;
import org.identityconnectors.framework.common.objects.filter.Filter;

import java.net.http.HttpResponse;
import java.util.*;

public class EndpointBasedSearchBuilder<BF, OF> implements FilterAwareSearchProcessorBuilder, RestSearchEndpointBuilder {

    final RestObjectClassDefinition objectClass;
    ResponseObjectExtractor<BF, OF> objectExtractor = r -> {
        if (r.body() instanceof ArrayNode array) {
            var ret = new ArrayList<OF>();
            array.elements().forEach(i -> ret.add((OF) i));
            return ret;
        }
        if (r.body() instanceof ObjectNode object) {
            return List.of((OF) object);
        }
        return List.of();
    };
    PagingHandler pagingHandler;
    Integer maxPageSize;
    Boolean emptyFilterSupported = null;
    final String path;
    Set<FilterToRequestMapper> filterMappers = new HashSet<>();
    Class<?> responseFormat = JSON_OBJECT;
    TotalCountExtractor<BF> totalCountExtractor = TotalCountExtractor.unsupported();
    QueryRequestBuilderImpl queryRequest = new QueryRequestBuilderImpl();

    public EndpointBasedSearchBuilder(String path, RestObjectClassDefinition objectClass) {
        this.path = path;
        this.objectClass = objectClass;
    }

    @Override
    public EndpointBasedSearchBuilder<BF, OF> objectExtractor(@DelegatesTo(value = ResponseWrapper.class, strategy = Closure.DELEGATE_FIRST) @Script.Runtime Closure<?> closure) {
        this.objectExtractor  = new GroovyObjectExtractor<>(closure);
        return this;
    }

    @Override
    public EndpointBasedSearchBuilder<BF, OF> pagingSupport(@DelegatesTo(value = PagingSupportBase.class, strategy = Closure.DELEGATE_FIRST) @Script.Runtime Closure<?> closure) {
        this.pagingHandler = new GroovyPagingSupport(closure);
        return this;
    }

    @Override
    public EndpointBasedSearchBuilder<BF, OF> maxPageSize(int maxPageSize) {
        this.maxPageSize = maxPageSize;
        return this;
    }

    @Override
    public void responseFormat(Class<?> responseFormat) {
        this.responseFormat = responseFormat;
    }

    @Override
    public EndpointBasedSearchBuilder<BF, OF>  singleResult() {
        this.totalCountExtractor = TotalCountExtractor.singleObject();
        return this;
    }

    @Override
    public EndpointBasedSearchBuilder<BF, OF>  emptyFilterSupported(boolean emptyFilterSupported) {
        this.emptyFilterSupported = emptyFilterSupported;
        return this;
    }

    @Override
    public void httpOperation(HttpMethod method) {
        // FIXME: Add support for other HTTP methods (probably POST)
        throw new UnsupportedOperationException(
                "HTTP method operations are not supported on search endpoints (endpoint '" + path + "' uses GET)");
    }

    @Override
    public boolean emptyFilterSupported() {
        return emptyFilterSupported;
    }

    @Override
    public FilterSpecification.Attribute attribute(String name) {
        // FIXME: Create deffered search here
        return objectClass.filterAttribute(name, "when defining a search filter for endpoint '" + path + "'");
    }

    @Override
    public RestSearchEndpointBuilder supportedFilter(FilterSpecification filterSpec) {
        // FIXME: implement builder here
        throw new UnsupportedOperationException(
                "supportedFilter without a mapping closure is not implemented on search endpoints (endpoint '" + path + "')");
    }

    @Override
    public EndpointBasedSearchBuilder<BF, OF> supportedFilter(FilterSpecification filterSpec, @DelegatesTo(FilterSupportBase.class) @Script.Runtime Closure<?> closure) {
        filterMappers.add(new GroovyBasedFilterHandler(filterSpec,closure));
        if (emptyFilterSupported == null) {
            // If empty filter support was not specified explicitly, we assume that it is not supported
            // when adding explicit filtering
            emptyFilterSupported = false;
        }
        return this;
    }

    @Override
    public QueryRequestBuilderImpl request() {
        return queryRequest;
    }

    public record GroovyObjectExtractor<BF, OF>(Closure<?> prototype) implements ResponseObjectExtractor<BF, OF> {

        @Override
        public Iterable<OF> extractObjects(HttpResponse<BF> response) {
            return GroovyClosures.copyAndCall((Closure<Iterable<OF>>) prototype, new ResponseWrapper<BF>(response));
        }
    }

    public record GroovyPagingSupport(Closure<?> prototype) implements PagingHandler {
        @Override
        public void handlePaging(HttpRequestSpecification request, PagingInfo pagingInfo) {
            GroovyClosures.copyAndCall(prototype, new PagingSupportBase(request, pagingInfo));
        }
    }

    private record GroovyBasedFilterHandler(FilterSpecification filterSpecification, Closure<?> prototype) implements FilterToRequestMapper {

        @Override
        public void mapToRequest(HttpRequestSpecification builder, Filter filter) {
            Object value = null;
            List<Object> values = List.of();
            if (filter instanceof AttributeFilter attrFilter && !attrFilter.getAttribute().getValue().isEmpty()) {
                values = attrFilter.getAttribute().getValue();
                value = values.getFirst();

            }
            GroovyClosures.copyAndCall(prototype, new FilterSupportBase(builder, filter, value, values));
        }
    }

    @Override
    public boolean isEnabled() {
        // Maybe do this also configurable?
        return true;
    }

    public EndpointBasedSearchHandler<BF, OF> build() {
        if (emptyFilterSupported == null && filterMappers.isEmpty()) {
            // No specific filter mappers were specified and empty filter support was not specified explicitly
            // so we assume that empty filter is supported
            emptyFilterSupported = true;
        }
        if (Boolean.TRUE.equals(emptyFilterSupported)) {
            filterMappers.add(FilterToRequestMapper.from(Objects::isNull, (r, f) -> {}));
        }
        return new EndpointBasedSearchHandler<>(this, filterMappers);
    }
}
