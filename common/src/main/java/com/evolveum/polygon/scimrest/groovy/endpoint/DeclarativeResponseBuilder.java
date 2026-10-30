/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.scimrest.groovy.endpoint;

import com.evolveum.polygon.conndev.api.AttributePathDeclaration;
import com.evolveum.polygon.conndev.json.JsonAttributeMapping;
import com.evolveum.polygon.scimrest.groovy.api.EndpointBuilder;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.function.Function;

public class DeclarativeResponseBuilder<T> implements EndpointBuilder.ResponseBuilder<T> {

    private AttributePathDeclaration<?, ?> objectPath;

    @Override
    public EndpointBuilder.ResponseBuilder<T> objectPath(AttributePathDeclaration<?, ?> declaration) {
        objectPath = declaration;
        return this;
    }

    public Function<Object, ObjectNode> buildObjectExtractor(String endpointPath) {
        var declaration = objectPath;
        // Parse before issuing any request, retaining the declaration's source location on errors.
        var path = declaration != null ? declaration.actual() : null;
        return body -> {
            if (!(body instanceof ObjectNode object)) {
                return null;
            }
            if (path == null) {
                return object.isEmpty() ? null : object;
            }
            var selected = path.resolve((JsonNode) object, JsonAttributeMapping.NULLABLE_PATH_RESOLVER);
            if (selected instanceof ObjectNode resource) {
                return resource;
            }
            throw new ConnectorException("Response object path '" + declaration.value().value()
                    + "' at endpoint " + endpointPath + " does not resolve to a JSON object");
        };
    }
}
