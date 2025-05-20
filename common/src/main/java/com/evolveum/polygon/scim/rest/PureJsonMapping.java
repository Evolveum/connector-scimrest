package com.evolveum.polygon.scim.rest;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Set;

public enum PureJsonMapping implements AttributeMapping {
    STRING("string", "A string value", String.class, String.class),
    INTEGER("integer", "A signed integer value", Integer.class, Integer.class),
    BOOLEAN("boolean", "A boolean value", Boolean.class, Boolean.class)
    ;

    private final Set<Class<?>> availableWireTypes;
    private final Class<?> connidClass;
    private final Class<?> primaryWireType;
    private final String jsonType;

    PureJsonMapping(String jsonType, String description, Class<?> connidClass, Class<?>... jsonClass) {
        this.jsonType = jsonType;
        this.primaryWireType = jsonClass[0];
        this.availableWireTypes = Set.of(jsonClass);
        this.connidClass = connidClass;
    }



    public Set<Class<?>> getJsonClasses() {
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
    public Class<?> primaryWireType() {
        return primaryWireType;
    }

    @Override
    public Set<Class<?>> supportedWireTypes() {
        return availableWireTypes;
    }

    @Override
    public Object toWireValue(Object value) throws IllegalArgumentException {
        // FIXME implement proper convertors
        return value;
    }

    @Override
    public Object toConnIdValue(Object value) throws IllegalArgumentException {
        // FIXME implement proper convertors
        return value;
    }

    public static AttributeMapping from(String jsonType) {
        for (PureJsonMapping am : values()) {
            if (am.jsonType.equals(jsonType)) {
                return am;
            }
        };
        return new AttributeMapping() {
            @Override
            public Class<?> connIdType() {
                return String.class;
            }

            @Override
            public Class<?> primaryWireType() {
                return Object.class;
            }

            @Override
            public Set<Class<?>> supportedWireTypes() {
                return Set.of(Object.class);
            }

            @Override
            public Object toWireValue(Object value) throws IllegalArgumentException {
                return value;
            }

            @Override
            public Object toConnIdValue(Object value) throws IllegalArgumentException {
                return value;
            }
        };
    }
}
