package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.RestContext;
import com.evolveum.polygon.scim.rest.groovy.spi.ResponseWrapper;
import com.evolveum.polygon.scim.rest.schema.RestObjectClass;
import com.evolveum.polygon.scim.rest.spi.SearchEndpointHandler;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.identityconnectors.framework.common.objects.filter.AttributeFilter;
import org.identityconnectors.framework.common.objects.filter.Filter;

import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SearchEndpointBuilder<BF, OF> {


    private final RestObjectClass objectClass;
    ResponseObjectExtractor<BF, OF> objectExtractor = r -> List.of((OF) r.body());
    PagingHandler pagingSupport;
    boolean emptyFilterSupported = false;
    final String path;
    Set<FilterToRequestMapper> filterMappers = new HashSet<>();

    public SearchEndpointBuilder(String path, RestObjectClass objectClass) {
        this.path = path;
        this.objectClass = objectClass;
    }

    public SearchEndpointBuilder<BF, OF> objectExtractor(@DelegatesTo(ResponseWrapper.class) Closure<?> closure) {
        this.objectExtractor  = new GroovyObjectExtractor<>(closure);
        return this;
    }

    public SearchEndpointBuilder<BF, OF> pagingSupport(@DelegatesTo(PagingSupportBase.class) Closure<?> closure) {
        this.pagingSupport = new GroovyPagingSupport(closure);
        return this;
    }

    public void emptyFilterSupported(boolean emptyFilterSupported) {
        this.emptyFilterSupported = emptyFilterSupported;
    }

    public SearchEndpointHandler<BF,OF> build() {
        if (emptyFilterSupported) {
            filterMappers.add(FilterToRequestMapper.from(f -> f == null, (r,f) -> {}));
        }
        return new FilterDispatchingEndpointHandler<>(this, filterMappers);
    }

    public FilterSpecification attribute(String name) {
        var connIdName = objectClass.attributeFromProtocolName(name).connId().getName();
        return FilterSpecification.attribute(connIdName);
    }

    public SearchEndpointBuilder<BF, OF> supportedFilter(FilterSpecification filterSpec, @DelegatesTo(FilterSupportBase.class) Closure<?> closure) {
        filterMappers.add(new GroovyBasedFilterHandler(filterSpec,closure));
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
}
