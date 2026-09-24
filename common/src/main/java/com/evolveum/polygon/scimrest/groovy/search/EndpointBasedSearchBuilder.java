/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy.search;

import com.evolveum.polygon.scimrest.groovy.endpoint.PathBasedObjectExtractor;
import com.evolveum.polygon.scimrest.groovy.endpoint.ResponseObjectExtractor;
import com.evolveum.polygon.scimrest.groovy.endpoint.QueryRequestBuilderImpl;

import com.evolveum.polygon.conndev.annotations.Script;
import com.evolveum.polygon.conndev.api.AttributePathDeclaration;
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
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.objects.filter.AttributeFilter;
import org.identityconnectors.framework.common.objects.filter.Filter;

import java.net.http.HttpResponse;
import java.util.*;

public class EndpointBasedSearchBuilder<BF, OF> implements FilterAwareSearchProcessorBuilder, RestSearchEndpointBuilder {

    final RestObjectClassDefinition objectClass;
    AttributePathDeclaration<?, ?> objectExtractorPath;
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
    Integer declarativePageSize;
    List<PagingParameter> declarativePagingParameters = new ArrayList<>();
    Boolean emptyFilterSupported = null;
    final String path;
    Set<FilterToRequestMapper> filterMappers = new HashSet<>();
    Class<?> responseFormat = JSON_OBJECT;
    TotalCountExtractor<BF> totalCountExtractor = TotalCountExtractor.unsupported();
    QueryRequestBuilderImpl queryRequest = new QueryRequestBuilderImpl();
    HttpMethod httpMethod = HttpMethod.GET;

    public EndpointBasedSearchBuilder(String path, RestObjectClassDefinition objectClass) {
        this.path = path;
        this.objectClass = objectClass;
    }

    @Override
    public EndpointBasedSearchBuilder<BF, OF> objectExtractor(@DelegatesTo(value = ResponseWrapper.class, strategy = Closure.DELEGATE_FIRST) @Script.Runtime Closure<?> closure) {
        this.objectExtractor  = new GroovyObjectExtractor<>(closure);
        this.objectExtractorPath = null;
        return this;
    }

    @Override
    public EndpointBasedSearchBuilder<BF, OF> objectExtractor(AttributePathDeclaration<?, ?> declaration) {
        this.objectExtractorPath = declaration;
        this.objectExtractor = new PathBasedObjectExtractor<>(declaration);
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
    public EndpointBasedSearchBuilder<BF, OF> pageSize(int size) {
        if (size < 1) {
            throw new IllegalArgumentException(
                    "Page size of search endpoint '" + path + "' must be at least 1, got " + size);
        }
        this.declarativePageSize = size;
        return this;
    }

    @Override
    public EndpointBasedSearchBuilder<BF, OF> pagingParameter(String token, String location, String name) {
        this.declarativePagingParameters.add(new PagingParameter(token, location, name));
        return this;
    }

    int pageLimit() {
        return declarativePageSize != null ? declarativePageSize : RestSearchOperationHandler.DEFAULT_PAGE_SIZE;
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
        if (method != HttpMethod.GET && method != HttpMethod.POST) {
            throw new UnsupportedOperationException(
                    "Search endpoint '" + path + "' supports only GET and POST, got " + method);
        }
        this.httpMethod = method;
    }

    HttpMethod httpMethod() {
        return httpMethod;
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

    /**
     * A declarative paging parameter: a paging token ({@code pageSize}, {@code page}, or
     * {@code offset}) mapped onto the request at the given location (query / header / body)
     * under the given name (which defaults to the token).
     */
    public record PagingParameter(String token, String location, String name) {
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
        if (objectExtractorPath != null) {
            // Fail fast at configuration time: an invalid path expression must surface with the
            // declared source location, not on the first search request.
            objectExtractorPath.actual();
        }
        if (!declarativePagingParameters.isEmpty() && pagingHandler != null) {
            throw new ConfigurationException(
                    "Search endpoint '" + path + "' declares both a pagingSupport closure and declarative paging parameters");
        }
        // Note: pageSize alone composes with a pagingSupport closure (it feeds the closure's
        // paging.pageSize); only the declarative parameters list is an alternative to the closure.
        if (!declarativePagingParameters.isEmpty()) {
            for (var parameter : declarativePagingParameters) {
                if (!"pageSize".equals(parameter.token()) && !"page".equals(parameter.token())
                        && !"offset".equals(parameter.token())) {
                    throw new ConfigurationException(
                            "Unknown paging token '" + parameter.token() + "' in search endpoint '" + path
                                    + "' (expected 'pageSize', 'page', or 'offset')");
                }
                if (!"query".equals(parameter.location()) && !"header".equals(parameter.location())
                        && !"body".equals(parameter.location())) {
                    throw new ConfigurationException(
                            "Unknown paging location '" + parameter.location() + "' in search endpoint '" + path
                                    + "' (expected 'query', 'header', or 'body')");
                }
                if ("body".equals(parameter.location()) && httpMethod != HttpMethod.POST) {
                    throw new ConfigurationException(
                            "Paging parameter '" + parameter.name() + "' of search endpoint '" + path
                                    + "' is mapped onto the request body, which requires the endpoint httpOperation to be POST");
                }
            }
            if (pagingHandler == null) {
                pagingHandler = new DeclarativePagingHandler(declarativePagingParameters);
            }
        }
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
