package com.evolveum.polygon.scimrest.groovy.api;


import com.evolveum.polygon.scimrest.groovy.GroovyClosures;
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

    interface RequestBuilder<I> extends GroovyContentTypeMixin {

        static final Function<Object, byte[]> EMPTY = (i) -> null;

        /**
         * Set's the content type using request
         *
         * @param contentType Request Body Content Type
         * @return
         */
        RequestBuilder<I> contentType(String contentType);

        RequestBuilder<I> accept(String... contentType);


        RequestBuilder<I> body(Function<? super I, byte[]> bodyTransformer);

        RequestBuilder<I> body(Closure<byte[]> bodyTransformer);

    }

    interface ResponseBuilder<O> extends GroovyContentTypeMixin {



    }
}
