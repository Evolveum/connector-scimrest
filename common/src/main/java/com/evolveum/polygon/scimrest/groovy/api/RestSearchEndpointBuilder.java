/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy.api;

import com.evolveum.polygon.conndev.annotations.Script;
import com.evolveum.polygon.conndev.api.AttributePathDeclaration;
import com.evolveum.polygon.conndev.api.BasicJsonPathFormat;
import com.evolveum.polygon.conndev.api.FilterSpecification;
import com.evolveum.polygon.conndev.build.api.SearchHandlerBuilder;
import com.evolveum.polygon.conndev.annotations.Yaml;
import com.evolveum.polygon.scimrest.api.HttpRequestSpecification;
import com.evolveum.polygon.scimrest.groovy.search.RestSearchOperationHandler;
import com.evolveum.polygon.scimrest.yaml.binding.ObjectExtractorHandler;
import com.evolveum.polygon.scimrest.yaml.binding.PagingSupportHandler;
import com.evolveum.polygon.scimrest.yaml.binding.ResponseFormatCoercer;
import com.evolveum.polygon.scimrest.yaml.binding.SingleResultHandler;
import com.evolveum.polygon.scimrest.yaml.binding.SupportedFiltersHandler;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.identityconnectors.framework.common.objects.filter.AttributeFilter;
import org.identityconnectors.framework.common.objects.filter.Filter;

import java.util.List;

public interface RestSearchEndpointBuilder extends EndpointBuilder, SearchHandlerBuilder<RestSearchEndpointBuilder>, EndpointBuilder.QueryEndpoint {

    Class<ArrayNode> JSON_ARRAY = ArrayNode.class;
    Class<ObjectNode> JSON_OBJECT = ObjectNode.class;


    /**
     * Sets the response format class for the search endpoint.
     *
     * The built-in supported response formats are {@link #JSON_ARRAY}, {@link #JSON_OBJECT}
     *
     * @param responseFormat The Class object representing the desired response format.
     */
    @Yaml.Key
    @Yaml.ValueParser(ResponseFormatCoercer.class)
    void responseFormat(Class<?> responseFormat);



    /**
     * Configures the paging support for the search endpoint using Groovy Closure.
     *
     * @param closure A closure that modifies request to include paging information.
     * @return This builder instance, allowing method chaining.
     */
     RestSearchEndpointBuilder pagingSupport(@Script.Runtime @DelegatesTo(value = PagingSupportBase.class, strategy = Closure.DELEGATE_FIRST) Closure<?> closure);

    /**
     * Marker for the YAML front-end: the {@code pagingSupport:} key is bound by
     * {@link PagingSupportHandler} — a mapping with {@code pageSize} and/or a {@code parameters}
     * mapping of paging tokens to {@code {in, name}} entries yields a declarative paging handler,
     * a string/block scalar keeps the Groovy form. The method body is unused.
     */
    @Yaml.Custom(PagingSupportHandler.class)
    default void pagingSupport() {
    }

    /**
     * Sets the number of remote objects requested per page when paging through the results
     * (the value the {@code pageSize} and {@code offset} paging tokens resolve with, and the
     * page size the retriever uses for its last-page detection). Defaults to
     * {@link RestSearchOperationHandler#DEFAULT_PAGE_SIZE}.
     */
    RestSearchEndpointBuilder pageSize(int size);

    /**
     * Adds a declarative paging parameter mapped onto the request for every page, with the
     * request parameter named after the token.
     *
     * @param token the paging token: {@code pageSize} (the requested page size), {@code page}
     *              (the page number, starting with 1), or {@code offset} (the zero-based object
     *              offset, {@code (page - 1) * pageSize})
     * @param location where the parameter goes: {@code query}, {@code header}, or {@code body}
     *                 (a request body; requires the endpoint's {@code httpOperation} to be POST)
     */
    default RestSearchEndpointBuilder pagingParameter(String token, String location) {
        return pagingParameter(token, location, token);
    }

    /**
     * Like {@link #pagingParameter(String, String)}, with an explicit request parameter name.
     */
    RestSearchEndpointBuilder pagingParameter(String token, String location, String name);


    @Yaml.Custom(SingleResultHandler.class)
    RestSearchEndpointBuilder singleResult();


    RestSearchEndpointBuilder objectExtractor(@Script.Runtime @DelegatesTo(value = ResponseWrapper.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure);

    /**
     * Marker for the YAML front-end: the {@code objectExtractor:} key is bound by
     * {@link ObjectExtractorHandler} — a {@code {type, value}} path mapping (JSON Pointer or basic
     * JSONPath) yields a declarative path-based extractor, a string/block scalar keeps the Groovy
     * form. The method body is unused.
     */
    @Yaml.Custom(ObjectExtractorHandler.class)
    default void objectExtractor() {
    }

    /**
     * Sets a declarative object extractor: an attribute path (JSON Pointer or basic JSONPath,
     * the same formats as the attribute {@code path} keys) resolved against the response body.
     * A resolved array yields its elements, a resolved object yields the single object, a missing
     * path yields no objects.
     */
    RestSearchEndpointBuilder objectExtractor(AttributePathDeclaration<?, ?> declaration);

    /**
     * Like {@link #objectExtractor(AttributePathDeclaration)}, with the expression written in the
     * default (basic JSONPath) format, e.g. {@code objectExtractor("$._embedded.elements")}.
     */
    default RestSearchEndpointBuilder objectExtractor(String pathExpression) {
        return objectExtractor(AttributePathDeclaration.of(BasicJsonPathFormat.INSTANCE, pathExpression));
    }

    RestSearchEndpointBuilder supportedFilter(FilterSpecification filterSpec, @DelegatesTo(value = FilterSupportBase.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure);

    /**
     * Marker for the YAML front-end: the {@code supportedFilters:} block is bound by
     * {@link SupportedFiltersHandler}; the method body is unused.
     */
    @Yaml.Custom(SupportedFiltersHandler.class)
    default void supportedFilters() {
    }


    record PagingSupportBase(HttpRequestSpecification request, PagingInfo paging) {
        public PagingInfo getPaging() {
            return paging;
        }

        public HttpRequestSpecification getRequest() {
            return request;
        }
    }

    record FilterSupportBase(HttpRequestSpecification request, Filter filter, Object value, List<Object> values) {

        public HttpRequestSpecification getRequest() {
            return request;
        }

        public Object getValue() {
            if (filter instanceof AttributeFilter attrFilter) {
                return attrFilter.getAttribute().getValue().getFirst();
            }
            return null;
        }

        public List<Object> getValues() {
            if (filter instanceof AttributeFilter attrFilter) {
                return attrFilter.getAttribute().getValue();
            }
            return null;
        }
    }
}
