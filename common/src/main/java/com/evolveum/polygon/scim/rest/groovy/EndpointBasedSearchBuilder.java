/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.RestContext;
import com.evolveum.polygon.scim.rest.groovy.api.FilterSpecification;
import com.evolveum.polygon.scim.rest.groovy.api.PagingInfo;
import com.evolveum.polygon.scim.rest.groovy.api.SearchEndpointBuilder;
import com.evolveum.polygon.scim.rest.groovy.api.ResponseWrapper;
import com.evolveum.polygon.scim.rest.schema.MappedObjectClass;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.identityconnectors.framework.common.objects.filter.AttributeFilter;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.http.HttpResponse;
import java.util.*;

public class EndpointBasedSearchBuilder<BF, OF> implements FilterAwareSearchProcessorBuilder, SearchEndpointBuilder {

    final MappedObjectClass objectClass;
    ResponseObjectExtractor<BF, OF> objectExtractor = r -> {
        if (r.body() instanceof ArrayNode array) {
            var ret = new ArrayList<OF>();
            array.elements().forEachRemaining(i -> ret.add((OF) i));
            return ret;
        }
        if (r.body() instanceof ObjectNode object) {
            return List.of((OF) object);
        }
        return List.of();
    };
    PagingHandler pagingSupport;
    Boolean emptyFilterSupported = null;
    final String path;
    Set<FilterToRequestMapper> filterMappers = new HashSet<>();
    Class<?> responseFormat = JSON_OBJECT;
    TotalCountExtractor<BF> totalCountExtractor = TotalCountExtractor.unsupported();

    public EndpointBasedSearchBuilder(String path, MappedObjectClass objectClass) {
        this.path = path;
        this.objectClass = objectClass;
    }

    @Override
    public EndpointBasedSearchBuilder<BF, OF> objectExtractor(@DelegatesTo(value = ResponseWrapper.class, strategy = Closure.DELEGATE_FIRST) Closure<?> closure) {
        this.objectExtractor  = new GroovyObjectExtractor<>(closure);
        return this;
    }

    @Override
    public EndpointBasedSearchBuilder<BF, OF> pagingSupport(@DelegatesTo(value = PagingSupportBase.class, strategy = Closure.DELEGATE_FIRST) Closure<?> closure) {
        this.pagingSupport = new GroovyPagingSupport(closure);
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
    public boolean emptyFilterSupported() {
        return emptyFilterSupported;
    }

    @Override
    public FilterSpecification.Attribute attribute(String name) {
        var connId = objectClass.attributeFromProtocolName(name).connId();
        if (connId != null) {
            // FIXME: Create deffered search here
            return FilterSpecification.attribute(connId.getName());
        }
        return FilterSpecification.attribute(name);
    }

    @Override
    public EndpointBasedSearchBuilder<BF, OF> supportedFilter(FilterSpecification filterSpec, @DelegatesTo(FilterSupportBase.class) Closure<?> closure) {
        filterMappers.add(new GroovyBasedFilterHandler(filterSpec,closure));
        if (emptyFilterSupported == null) {
            // If empty filter support was not specified explicitly, we assume that it is not supported
            // when adding explicit filtering
            emptyFilterSupported = false;
        }
        return this;
    }

    public record GroovyObjectExtractor<BF, OF>(Closure<?> prototype) implements ResponseObjectExtractor<BF, OF> {

        @Override
        public Iterable<OF> extractObjects(HttpResponse<BF> response) {
            return GroovyClosures.copyAndCall((Closure<Iterable<OF>>) prototype, new ResponseWrapper<BF>(response));
        }
    }

    public record GroovyPagingSupport(Closure<?> prototype) implements PagingHandler {
        @Override
        public void handlePaging(RestContext.RequestBuilder request, PagingInfo pagingInfo) {
            GroovyClosures.copyAndCall(prototype, new PagingSupportBase(request, pagingInfo));
        }
    }



    private record GroovyBasedFilterHandler(FilterSpecification filterSpecification, Closure<?> prototype) implements FilterToRequestMapper {

        @Override
        public void mapToRequest(RestContext.RequestBuilder builder, Filter filter) {
            Object value = null;
            List<Object> values = List.of();
            if (filter instanceof AttributeFilter attrFilter && !attrFilter.getAttribute().getValue().isEmpty()) {
                values = attrFilter.getAttribute().getValue();
                value = values.get(0);

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
