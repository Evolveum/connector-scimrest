/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy.api;

import com.evolveum.polygon.conndev.annotations.Script;
import com.evolveum.polygon.conndev.api.FilterSpecification;
import com.evolveum.polygon.conndev.build.api.SearchHandlerBuilder;
import com.evolveum.polygon.conndev.annotations.Yaml;
import com.evolveum.polygon.scimrest.api.HttpRequestSpecification;
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


    @Yaml.Custom(SingleResultHandler.class)
    RestSearchEndpointBuilder singleResult();


    RestSearchEndpointBuilder objectExtractor(@Script.Runtime @DelegatesTo(value = ResponseWrapper.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure);

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
