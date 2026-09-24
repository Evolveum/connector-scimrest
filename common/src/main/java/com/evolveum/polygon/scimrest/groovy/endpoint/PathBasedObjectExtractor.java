/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy.endpoint;

import com.evolveum.polygon.conndev.api.AttributePathDeclaration;
import com.evolveum.polygon.conndev.json.JsonAttributeMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * A declarative {@link ResponseObjectExtractor} backed by an attribute path (JSON Pointer or
 * basic JSONPath, see {@link AttributePathDeclaration}) instead of a Groovy closure.
 *
 * <p>The path is resolved against the response body with the same nullable JSON resolver the
 * attribute schema mappings use. A resolved array yields its elements, a resolved object yields
 * the single object, and a missing (or empty body) path yields no objects.</p>
 */
public record PathBasedObjectExtractor<BF, OF>(AttributePathDeclaration<?, ?> declaration)
        implements ResponseObjectExtractor<BF, OF> {

    @Override
    public Iterable<OF> extractObjects(HttpResponse<BF> response) {
        var resolved = declaration.actual().resolve(
                (JsonNode) response.body(), JsonAttributeMapping.NULLABLE_PATH_RESOLVER);
        if (resolved == null) {
            return List.of();
        }
        if (resolved instanceof ArrayNode array) {
            var ret = new ArrayList<OF>();
            array.elements().forEach(i -> ret.add((OF) i));
            return ret;
        }
        return List.of((OF) resolved);
    }
}
