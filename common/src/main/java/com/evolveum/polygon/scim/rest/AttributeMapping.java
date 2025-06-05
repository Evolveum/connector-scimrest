package com.evolveum.polygon.scim.rest;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Represents a mapping between data types used in an ConnId
 * and their corresponding wire types used in the protocol representation.
 * This interface allows for conversions between these two types and provides
 * metadata about the supported data types.
 */
public interface AttributeMapping {

    Class<?> connIdType();
    Class<?> primaryWireType();
    default Set<Class<?>> supportedWireTypes() {
        return Set.of(primaryWireType());
    }

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
        var connIdValue = toConnIdValue(wireValues);
        if (connIdValue != null) {
            return List.of(connIdValue);
        }
        return null;
    }
}
