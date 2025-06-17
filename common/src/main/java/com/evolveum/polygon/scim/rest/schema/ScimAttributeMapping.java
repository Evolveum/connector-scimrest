package com.evolveum.polygon.scim.rest.schema;

import com.evolveum.polygon.scim.rest.ValueMapping;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ValueNode;

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
    public Class<?> connIdType() {
        return null;
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

        private final ValueMapping implementation;

        public Custom(String name, String type, ValueMapping implementation) {
            super(name);
            this.implementation = implementation;
        }

        @Override
        Object convertSingleValue(JsonNode value) {
            return implementation.toConnIdValue(value);
        }

        @Override
        public List<Object> extractMultipleValues(ObjectNode wireValues) {
            return implementation.toConnIdValues(wireValues);
        }
    }

    public static class ValueMappingBased extends ScimAttributeMapping {

        private ValueMapping<?, JsonNode> mapping;

        public ValueMappingBased(String name, ValueMapping<?, JsonNode> mapping) {
            super(name);
            if (mapping == null) {
                throw new IllegalArgumentException("Mapping is null");
            }
            this.mapping = mapping;
        }

        @Override
        Object convertSingleValue(JsonNode value) {
            return mapping.toConnIdValue(value);
        }

        @Override
        public Class<?> connIdType() {
            return mapping.connIdType();
        }

        @Override
        public List<Object> extractMultipleValues(ObjectNode wireValues) {
            return (List) mapping.toConnIdValues(wireValues);
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

        @Override
        public List<Object> extractMultipleValues(ObjectNode wireValues) {
            return List.of(convertSingleValue(wireValues));
        }
    }
}
