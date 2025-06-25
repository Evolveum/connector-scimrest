/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scim.rest.schema;

import com.evolveum.polygon.scim.rest.JsonValueMapping;
import com.evolveum.polygon.scim.rest.ValueMapping;
import com.evolveum.polygon.scim.rest.api.AttributePath;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

public class JsonAttributeMapping implements AttributeProtocolMapping<ObjectNode, JsonNode> {

    protected final AttributePath path;
    protected final ValueMapping<Object, JsonNode> valueMapping;

    public JsonAttributeMapping(String name,
                                ValueMapping<Object, JsonNode> valueMapping) {
        this(AttributePath.of(name), valueMapping);
    }

    public JsonAttributeMapping(AttributePath path,
                                ValueMapping<Object, JsonNode> valueMapping) {
        this.path = path;
        this.valueMapping = valueMapping;
    }



    @Override
    public Class<?> connIdType() {
        return valueMapping.connIdType();
    }

    @Override
    public JsonNode attributeFromObject(ObjectNode object) {
        if (path != null) {
            return path.resolve(object);
        }
        // FIXME: Here should be some other way for navigating structures (for flattening, similar to SCIM)
        return null;

    }

    @Override
    public Object singleValueFromAttribute(JsonNode attribute) {
        return valueMapping.toConnIdValue(attribute);
    }

    @Override
    public List<Object> valuesFromAttribute(JsonNode attribute) {
        if (attribute instanceof ArrayNode arrayNode) {
            var ret = new ArrayList<>();
            for (var value : arrayNode) {
                ret.add(singleValueFromAttribute(value));
            }
            return ret;
        }
        var maybeVal = singleValueFromAttribute(attribute);
        if (maybeVal != null) {
            return List.of(maybeVal);
        }
        return null;
    }
}
