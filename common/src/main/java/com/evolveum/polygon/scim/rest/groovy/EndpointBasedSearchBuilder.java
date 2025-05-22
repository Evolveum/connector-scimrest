package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.RestContext;
import com.evolveum.polygon.scim.rest.groovy.spi.ResponseWrapper;
import com.evolveum.polygon.scim.rest.schema.RestObjectClass;
import com.evolveum.polygon.scim.rest.spi.SearchEndpointHandler;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.identityconnectors.framework.common.objects.filter.AttributeFilter;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class EndpointBasedSearchBuilder<HC, BF, OF> implements FilterAwareSearchProcessorBuilder<HC> {

    public static final Class<JSONArray> JSON_ARRAY = JSONArray.class;
    public static final Class<JSONObject> JSON_OBJECT = JSONObject.class;


    final RestObjectClass objectClass;
    ResponseObjectExtractor<BF, OF> objectExtractor = r -> {
        if (r.body() instanceof JSONArray array) {
            return (Iterable<OF>)  array;
        }
        return List.of((OF) r.body());
    };
    PagingHandler pagingSupport;
    Boolean emptyFilterSupported = null;
    final String path;
    Set<FilterToRequestMapper> filterMappers = new HashSet<>();
    Class<?> responseFormat = JSON_OBJECT;
    TotalCountExtractor<BF> totalCountExtractor = TotalCountExtractor.unsupported();

    public EndpointBasedSearchBuilder(String path, RestObjectClass objectClass) {
        this.path = path;
        this.objectClass = objectClass;
    }

    public EndpointBasedSearchBuilder<HC, BF, OF> objectExtractor(@DelegatesTo(value = ResponseWrapper.class, strategy = Closure.DELEGATE_FIRST) Closure<?> closure) {
        this.objectExtractor  = new GroovyObjectExtractor<>(closure);
        return this;
    }

    /**
     * Configures the paging support for the search endpoint using Groovy Closure.
     *
     * @param closure A closure that modifies request to include paging information.
          * @return This builder instance, allowing method chaining.
     */
    public EndpointBasedSearchBuilder<HC, BF, OF> pagingSupport(@DelegatesTo(value = PagingSupportBase.class, strategy = Closure.DELEGATE_FIRST) Closure<?> closure) {
        this.pagingSupport = new GroovyPagingSupport(closure);
        return this;
    }

    /**
     * Sets the response format class for the search endpoint.
     *
     * The built-in supported response formats are {@link #JSON_ARRAY}, {@link #JSON_OBJECT}
     *
     * @param responseFormat The Class object representing the desired response format.
     */
    public void responseFormat(Class<?> responseFormat) {
        this.responseFormat = responseFormat;
    }

    public void singleResult() {
        this.totalCountExtractor = TotalCountExtractor.singleObject();
    }

    /**
     * Sets whether the search endpoint supports filtering with empty filter criteria.
     *
     * Only one such endpoint / custom script may be defined for whole search handler.
     *
     * @param emptyFilterSupported true if the endpoint should be used for searches without filters.
     */
    public void emptyFilterSupported(boolean emptyFilterSupported) {
        this.emptyFilterSupported = emptyFilterSupported;
    }

    @Override
    public boolean emptyFilterSupported() {
        return emptyFilterSupported;
    }

    public FilterSpecification attribute(String name) {
        var connIdName = objectClass.attributeFromProtocolName(name).connId().getName();
        return FilterSpecification.attribute(connIdName);
    }

    public EndpointBasedSearchBuilder<HC, BF, OF> supportedFilter(FilterSpecification filterSpec, @DelegatesTo(FilterSupportBase.class) Closure<?> closure) {
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
            return GroovyClosures.copyAndCall(prototype, new ResponseWrapper<BF>(response));
        }
    }

    public record GroovyPagingSupport(Closure<?> prototype) implements PagingHandler {
        @Override
        public void handlePaging(RestContext.RequestBuilder request, PagingInfo pagingInfo) {
            GroovyClosures.copyAndCall(prototype, new PagingSupportBase(request, pagingInfo));
        }
    }

    public record PagingSupportBase(RestContext.RequestBuilder request, PagingInfo paging) {

    }

    public record FilterSupportBase(RestContext.RequestBuilder request, Filter filter, Object value, List<Object> values) {


        public Object value() {
            if (filter instanceof AttributeFilter attrFilter) {
                return attrFilter.getAttribute().getValue().get(0);
            }
            return null;
        }

        public List<Object> values() {
            if (filter instanceof AttributeFilter attrFilter) {
                return attrFilter.getAttribute().getValue();
            }
            return null;
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

    public EndpointBasedSearchHandler<HC, BF, OF> build() {
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
