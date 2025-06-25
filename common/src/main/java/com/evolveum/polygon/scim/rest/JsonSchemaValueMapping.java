/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scim.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;

import java.util.Base64;
import java.util.Set;

public enum JsonSchemaValueMapping implements JsonValueMapping {
    STRING("string", "A string value", String.class, TextNode.class) {
        @Override
        public Object toConnIdValue(JsonNode value) throws IllegalArgumentException {
            if (value instanceof TextNode textNode) {
                return textNode.asText();
            }
            return super.toConnIdValue(value);
        }
    },
    INTEGER("integer", "A signed integer value", Integer.class, IntNode.class) {
        @Override
        public Object toConnIdValue(JsonNode value) throws IllegalArgumentException {
            if (value instanceof IntNode intNode) {
                return intNode.asInt();
            }
            if (value instanceof NumericNode numericNode) {
                return numericNode.asInt();
            }
            return super.toConnIdValue(value);
        }

        @Override
        public JsonNode toWireValue(Object value) throws IllegalArgumentException {
            if (value instanceof Integer) {
                return new IntNode((Integer) value);
            }
            return super.toWireValue(value);
        }
    },
    BOOLEAN("boolean", "A boolean value", Boolean.class, BooleanNode.class) {
        @Override
        public Object toConnIdValue(JsonNode value) throws IllegalArgumentException {
            if (value instanceof BooleanNode booleanNode) {
                return booleanNode.asBoolean();
            }
            return super.toConnIdValue(value);
        }

        @Override
        public JsonNode toWireValue(Object value) throws IllegalArgumentException {
            if (value instanceof Boolean bool) {
                return BooleanNode.valueOf(bool);
            }
            return super.toWireValue(value);
        }
    },
    NUMBER("number", "A number value", Number.class, NumericNode.class) {

        @Override
        public JsonNode toWireValue(Object value) throws IllegalArgumentException {
            if (value instanceof Number n) {
                return JsonNodeFactory.instance.numberNode(n.doubleValue());
            }
        return super.toWireValue(value);
        }

        @Override
        public Object toConnIdValue(JsonNode value) throws IllegalArgumentException {
            if (value instanceof NumericNode numeric) {
                return numeric.numberValue();
            }
            return super.toConnIdValue(value);
        }
    },
    BINARY("binary", "A binary value", BinaryNode.class, TextNode.class) {
        public JsonNode toWireValue(Object value) throws IllegalArgumentException {
            if (value instanceof byte[] byteArrayVal) {
                return new BinaryNode(byteArrayVal);
            }
            throw new IllegalArgumentException("Cannot convert " + value.getClass() + " to " + this.getClass().getSimpleName());
        }

        @Override
        public Object toConnIdValue(JsonNode value) throws IllegalArgumentException {
            if (value instanceof BinaryNode textNode) {
                return textNode.binaryValue();
            }
            if (value instanceof TextNode textNode) {
                return Base64.getDecoder().decode(textNode.asText());
            }
            throw new IllegalArgumentException("Cannot convert " + value.getClass() + " to " + this.getClass().getSimpleName());
        }

    }
    ;

    private final Set<Class<? extends JsonNode>> availableWireTypes;
    private final Class<?> connidClass;
    private final Class<? extends JsonNode> primaryWireType;
    private final String jsonType;

    JsonSchemaValueMapping(String jsonType, String description,
                           Class<?> connidClass, Class<? extends JsonNode>... jsonClass) {
        this.jsonType = jsonType;
        this.primaryWireType = jsonClass[0];
        this.availableWireTypes = Set.of(jsonClass);
        this.connidClass = connidClass;
    }



    public Set<Class<? extends JsonNode>> getJsonClasses() {
        return availableWireTypes;
    }

    public Class<?> connIdClass() {
        return connidClass;
    }

    @Override
    public Class<?> connIdType() {
        return connidClass;
    }

    @Override
    public Class<? extends JsonNode> primaryWireType() {
        return primaryWireType;
    }

    @Override
    public Set<Class<? extends JsonNode>> supportedWireTypes() {
        return availableWireTypes;
    }

    @Override
    public Object toConnIdValue(JsonNode value) throws IllegalArgumentException {
        if (value == null) {
            return null;
        }
        throw new IllegalArgumentException("Can not convert " + value + " to a " + connidClass.getSimpleName());
    }

    @Override
    public JsonNode toWireValue(Object value) throws IllegalArgumentException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public static JsonValueMapping from(String jsonType) {
        for (JsonSchemaValueMapping am : values()) {
            if (am.jsonType.equals(jsonType)) {
                return am;
            }
        };
        return null;
    }
}
