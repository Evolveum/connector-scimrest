package com.evolveum.polygon.scimrest.groovy;

import com.evolveum.polygon.scimrest.groovy.api.EndpointBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class DeclarativeAcceptContentTypesBuilder<T> implements EndpointBuilder.QueryRequestBuilder<T> {

    protected List<String> acceptContentTypes = new ArrayList<>();

    @Override
    public EndpointBuilder.QueryRequestBuilder<T> accept(String... contentType) {
        this.acceptContentTypes.addAll(Arrays.asList(contentType));
        return this;
    }
}
