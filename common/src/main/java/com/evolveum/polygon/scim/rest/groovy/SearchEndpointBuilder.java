package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.RestContext;
import com.evolveum.polygon.scim.rest.groovy.spi.ResponseWrapper;
import com.evolveum.polygon.scim.rest.spi.SearchEndpointHandler;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

import java.net.http.HttpResponse;

public class SearchEndpointBuilder<BF, OF> {


    ResponseObjectExtractor<BF, OF> objectExtractor;
    PagingHandler pagingSupport;
    boolean emptyFilterSupported = true;
    final String path;


    public SearchEndpointBuilder(String path) {
        this.path = path;
    }

    public SearchEndpointBuilder<BF, OF> objectExtractor(@DelegatesTo(ResponseWrapper.class) Closure<?> closure) {
        this.objectExtractor  = new GroovyObjectExtractor<>(closure);
        return this;
    }

    public SearchEndpointBuilder<BF, OF> pagingSupport(@DelegatesTo(ResponseWrapper.class) Closure<?> closure) {
        this.pagingSupport = new GroovyPagingSupport(closure);
        return this;
    }

    public void emptyFilterSupported(boolean emptyFilterSupported) {
        this.emptyFilterSupported = emptyFilterSupported;
    }

    public SearchEndpointHandler<BF,OF> build() {
        return new FilterDispatchingEndpointHandler(this);
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
}
