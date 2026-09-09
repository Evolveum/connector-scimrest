/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.scimrest.groovy.endpoint;

import com.evolveum.polygon.scimrest.groovy.api.EndpointBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class DeclarativeAcceptContentTypesBuilder<T> implements EndpointBuilder.QueryRequestBuilder<T> {

    public List<String> acceptContentTypes = new ArrayList<>();

    @Override
    public EndpointBuilder.QueryRequestBuilder<T> accept(String... contentType) {
        this.acceptContentTypes.addAll(Arrays.asList(contentType));
        return this;
    }
}
