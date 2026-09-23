/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy.endpoint;

import com.evolveum.polygon.scimrest.groovy.api.EndpointBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

public class QueryRequestBuilderImpl extends DeclarativeAcceptContentTypesBuilder implements EndpointBuilder.QueryRequestBuilder  {

    public String contentType;
    public final Map<String, Object> bodyParameters = new LinkedHashMap<>();

    @Override
    public EndpointBuilder.QueryRequestBuilder contentType(String contentType) {
        this.contentType = contentType;
        return this;
    }

    @Override
    public EndpointBuilder.QueryRequestBuilder bodyParameter(String name, Object value) {
        if (value != null) {
            bodyParameters.put(name, value);
        }
        return this;
    }
}
