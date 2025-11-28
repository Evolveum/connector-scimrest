package com.evolveum.polygon.scimrest.groovy;

import com.evolveum.polygon.scimrest.groovy.api.EndpointBuilder;
import groovy.lang.Closure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public abstract class DeclarativeRequestBuilder<T> implements EndpointBuilder.RequestBuilder<T> {

    protected String contentType;
    protected List<String> acceptContentTypes = new ArrayList<>();
    protected Function<? super T, byte[]> bodyTransformer;

    @Override
    public EndpointBuilder.RequestBuilder<T> contentType(String contentType) {
        this.contentType = contentType;
        return this;
    }

    @Override
    public EndpointBuilder.RequestBuilder<T> accept(String... contentType) {
        this.acceptContentTypes.addAll(Arrays.asList(contentType));
        return this;
    }

    @Override
    public EndpointBuilder.RequestBuilder<T> body(Function<? super T, byte[]> bodyTransformer) {
        this.bodyTransformer = bodyTransformer;
        return this;
    }

}
