package com.evolveum.polygon.scim.rest;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public interface AttributeMapping {

    Class<?> connIdType();
    Class<?> primaryWireType();
    Set<Class<?>> supportedWireTypes();

    Object toWireValue(Object value) throws  IllegalArgumentException;
    Object toConnIdValue(Object value) throws IllegalArgumentException;

    default List<Object> toConnIdValues(Object wireValues) {
        if (wireValues == null) {
            return null;
        }
        if (wireValues instanceof Iterable<?> iterable) {
            var ret = new ArrayList<>();
            for (var w : iterable) {
                ret.add(toConnIdValue(w));
            }
            return ret;
        }
        return List.of(toConnIdValue(wireValues));
    }
}
