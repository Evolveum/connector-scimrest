package com.evolveum.polygon.scim.rest.schema;

import com.evolveum.polygon.scim.rest.AttributeMapping;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ValueNode;
import com.unboundid.scim2.common.GenericScimResource;

import java.util.ArrayList;
import java.util.List;

public abstract class ScimAttributeMapping implements AttributeProtocolMapping<ObjectNode> {

    private final String name;

    public ScimAttributeMapping(String name) {
        this.name = name;
    }

    public String path() {
        return name;
    }

    @Override
    public Object extractValue(ObjectNode remoteObj) { // FIXME: Here we should also do transformations
        var value = remoteObj.get(name);
        if (value instanceof ArrayNode arrayNode) {
            return convertToConnId(arrayNode);
        }
        return convertSingleValue(value);
    }

    private List<Object> convertToConnId(ArrayNode arrayNode) {
        var ret = new ArrayList<Object>();
        for (var value : arrayNode) {
            ret.add(convertSingleValue(value));
        }
        return ret;
    }

    abstract Object convertSingleValue(JsonNode value);

    public static class Custom extends ScimAttributeMapping {

        private final AttributeMapping implementation;

        public Custom(String name, String type, AttributeMapping implementation) {
            super(name);
            this.implementation = implementation;
        }

        @Override
        Object convertSingleValue(JsonNode value) {
            return implementation.toConnIdValue(value);
        }
    }

    public static class Naive extends ScimAttributeMapping {
        public Naive(String name) {
            super(name);
        }

        @Override
        Object convertSingleValue(JsonNode value)  {
            if (value instanceof ValueNode valueNode) {
                return valueNode.asText();
            }
            return value;
        }


    }
}
