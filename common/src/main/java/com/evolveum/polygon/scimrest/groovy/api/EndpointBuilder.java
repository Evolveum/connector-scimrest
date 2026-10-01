/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.scimrest.groovy.api;


import com.evolveum.polygon.conndev.annotations.Script;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import com.evolveum.polygon.conndev.annotations.Yaml;
import com.evolveum.polygon.scimrest.yaml.binding.QueryParametersHandler;
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
                    return attr.getValue().getFirst();
                }
                throw new IllegalArgumentException("Multiple values found for attribute " + name);
            };
        }

        void pathParameter(String name, Function<ConnectorObject, Object> extractor);

        @Yaml.Sub
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

        @Yaml.Sub
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
        @Yaml.Key
        @Yaml.Shortcut({"APPLICATION_JSON", "APPLICATION_XML", "APPLICATION_YAML", "APPLICATION_HAL_JSON"})
        RequestBuilder<I> contentType(String contentType);


        @Override
        @Yaml.Key
        @Yaml.Shortcut({"EMPTY"})
        RequestBuilder<I> body(Function<? super I, byte[]> bodyTransformer);

        @Override
        RequestBuilder<I> body(@Script.Runtime Closure<byte[]> bodyTransformer);

        /**
         * Adds a static query parameter to every request the endpoint issues.
         *
         * @param name the query parameter name
         * @param value the parameter value (stringified into the URL; {@code null} adds nothing)
         */
        RequestBuilder<I> queryParameter(String name, Object value);

        /**
         * Marker for the YAML front-end: the {@code queryParameters:} mapping (parameter name to
         * scalar value) under {@code request:} is bound by
         * {@link com.evolveum.polygon.scimrest.yaml.binding.QueryParametersHandler}; the method
         * body is unused.
         */
        @Yaml.Custom(QueryParametersHandler.class)
        default void queryParameters() {
        }
    }

    interface QueryRequestBuilder<I> extends RequestHeadersBuilder<I>{

        @Override
        QueryRequestBuilder<I> accept(String... contentType);

        /**
         * Sets the {@code Content-Type} of the (usually POST) search request.
         */
        @Yaml.Key
        @Yaml.Shortcut({"APPLICATION_JSON", "APPLICATION_XML", "APPLICATION_YAML", "APPLICATION_HAL_JSON"})
        QueryRequestBuilder<I> contentType(String contentType);

        /**
         * Adds a static field to the (usually POST) search request's JSON body.
         *
         * @param name the JSON field name
         * @param value the field value ({@code null} adds nothing)
         */
        QueryRequestBuilder<I> bodyParameter(String name, Object value);

        /**
         * Adds a static query parameter to every request the search endpoint issues.
         *
         * @param name the query parameter name
         * @param value the parameter value (stringified into the URL; {@code null} adds nothing)
         */
        QueryRequestBuilder<I> queryParameter(String name, Object value);

        /**
         * Marker for the YAML front-end: the {@code queryParameters:} mapping (parameter name to
         * scalar value) under {@code request:} is bound by
         * {@link com.evolveum.polygon.scimrest.yaml.binding.QueryParametersHandler}; the method
         * body is unused.
         */
        @Yaml.Custom(QueryParametersHandler.class)
        default void queryParameters() {
        }
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
