/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.scimrest.groovy;

import com.evolveum.polygon.scimrest.groovy.api.EndpointBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
