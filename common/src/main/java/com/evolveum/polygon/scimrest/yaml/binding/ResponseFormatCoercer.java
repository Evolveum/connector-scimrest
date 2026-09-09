/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.yaml.binding;

import com.evolveum.polygon.conndev.concepts.SourceLocation;
import com.evolveum.polygon.conndev.yaml.decl.DeclYamlValueParser;
import com.evolveum.polygon.conndev.yaml.decl.LocatedNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Coerces a {@code responseFormat} value (the built-in constant names {@code JSON_ARRAY}/
 * {@code JSON_OBJECT}) to the corresponding Jackson node class a search endpoint unmarshals into.
 */
public final class ResponseFormatCoercer implements DeclYamlValueParser {

    @Override
    public Object coerce(LocatedNode value, SourceLocation location, Class<?> targetType) {
        var name = value.text();
        return switch (name) {
            case "JSON_ARRAY" -> ArrayNode.class;
            case "JSON_OBJECT" -> ObjectNode.class;
            default -> throw new IllegalArgumentException("Unknown response format '" + name + "' at " + location);
        };
    }
}
