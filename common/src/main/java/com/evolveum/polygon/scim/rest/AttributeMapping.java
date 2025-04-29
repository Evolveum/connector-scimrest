package com.evolveum.polygon.scim.rest;

import java.util.Set;

public interface AttributeMapping {

    Class<?> connIdType();
    Class<?> primaryWireType();
    Set<Class<?>> supportedWireTypes();

    Object toWireValue(Object value) throws  IllegalArgumentException;
    Object toConnIdValue(Object value) throws IllegalArgumentException;

}
