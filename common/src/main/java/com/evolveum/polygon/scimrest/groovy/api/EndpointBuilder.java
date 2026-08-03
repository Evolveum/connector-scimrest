package com.evolveum.polygon.scimrest.groovy.api;


import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.identityconnectors.framework.common.objects.ConnectorObject;

import java.util.function.Function;

public interface EndpointBuilder extends GroovyHttpOperationMixin {

    void httpOperation(HttpMethod method);

    interface SingleObject<I,O> extends EndpointBuilder {

        default Function<ConnectorObject,Object> attribute(String name) {
            return connObj -> {
                var attr = connObj.getAttributeByName(name);
                if (attr == null) {
                    return null;
                }
                if (attr.getValue().isEmpty()) {
                    return null;
                }
                if (attr.getValue().size() == 1) {
                    return attr.getValue().get(0);
                }
                throw new IllegalArgumentException("Multiple values found for attribute " + name);
            };
        }

        void pathParameter(String name, Function<ConnectorObject, Object> extractor);

        RequestBuilder<I> request();

        default RequestBuilder<I> request(@DelegatesTo(value = RequestBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure) {
            return GroovyClosures.callAndReturnDelegate(closure, request());
        }

        ResponseBuilder<O> response();

        default ResponseBuilder<O> response(@DelegatesTo(value = RequestBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure) {
            return GroovyClosures.callAndReturnDelegate(closure, response());
        }
    }

    interface QueryEndpoint<I> extends EndpointBuilder {

        QueryRequestBuilder<I> request();

        default QueryRequestBuilder<I> request(@DelegatesTo(value = QueryRequestBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure) {
            return GroovyClosures.callAndReturnDelegate(closure, request());
        }

    }

    interface RequestBuilder<I>
            extends RequestHeadersBuilder<I>, RequestEntityBuilder<I> {

        @Override
        RequestBuilder<I> accept(String... contentType);

        @Override
        RequestBuilder<I> contentType(String contentType);

        @Override
        RequestBuilder<I> body(Function<? super I, byte[]> bodyTransformer);

        @Override
        RequestBuilder<I> body(Closure<byte[]> bodyTransformer);
    }

    interface QueryRequestBuilder<I> extends RequestHeadersBuilder<I>{

        @Override
        QueryRequestBuilder<I> accept(String... contentType);
    }

    interface RequestHeadersBuilder<I> extends GroovyContentTypeMixin {
        RequestHeadersBuilder<I> accept(String... contentType);
    }

    interface RequestEntityBuilder<I> extends GroovyContentTypeMixin {
        Function<Object, byte[]> EMPTY = i -> null;

        RequestEntityBuilder<I> contentType(String contentType);
        RequestEntityBuilder<I> body(Function<? super I, byte[]> bodyTransformer);
        RequestEntityBuilder<I> body(Closure<byte[]> bodyTransformer);
    }

    interface ResponseBuilder<O> extends GroovyContentTypeMixin {



    }
}
