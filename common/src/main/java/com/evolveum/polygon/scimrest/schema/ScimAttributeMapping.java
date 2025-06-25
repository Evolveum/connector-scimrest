/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.schema;

import com.evolveum.polygon.scimrest.ValueMapping;
import com.evolveum.polygon.scimrest.api.AttributePath;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class ScimAttributeMapping extends JsonAttributeMapping {

    public ScimAttributeMapping(AttributePath path, ValueMapping<Object, JsonNode> mapping) {
        super(path, mapping);
    }

    @Override
    public JsonNode attributeFromObject(ObjectNode object) {
        if (path != null) {
            return path.resolve(object);
        }
        return null;
    }

}
