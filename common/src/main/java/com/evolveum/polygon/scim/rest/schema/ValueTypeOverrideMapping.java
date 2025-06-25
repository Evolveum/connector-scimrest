/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scim.rest.schema;

import com.evolveum.polygon.scim.rest.ValueMapping;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

record ValueTypeOverrideMapping<O, D, P>(Class<O> connIdType, ValueMapping<D,P> impl, Function<O,D> toDelegate, Function<D, O> toOverride)  implements ValueMapping<O,P> {

    public static <P> ValueMapping<Object, P> of(Class<?> connIdType, ValueMapping<?, P> valueMapping) {
        if (Objects.equals(connIdType, valueMapping.connIdType())) {
            return cast(Object.class, valueMapping);
        }
        if (String.class.equals(connIdType)) {
            return cast(Object.class, stringType(valueMapping));
        }
        throw new IllegalArgumentException("Unsupported override type combination: " + connIdType + " and " + valueMapping.toString());
    }

    private static <P> ValueMapping<String,P> stringType(ValueMapping<?,P> valueMapping) {
        var original = valueMapping.connIdType();
        if (Integer.class.equals(original)) {
            return new ValueTypeOverrideMapping<>(String.class, cast(Integer.class,valueMapping),
                Integer::parseInt, Object::toString
            );
        }
        if (Long.class.equals(original)) {
            return new ValueTypeOverrideMapping<>(String.class, cast(Long.class, valueMapping),
                    Long::parseLong, Object::toString);
        }
        throw new IllegalArgumentException("Unsupported override type combination: " + valueMapping.connIdType());
    }
    // FIXME: Add other conversion for ConnID
    @SuppressWarnings("unchecked")
    private static <O, P> ValueMapping<O,P> cast(Class<O> type, ValueMapping<?,P> valueMapping) {
        return (ValueMapping<O, P>) valueMapping;
    }


    @Override
    public Class<O> connIdType() {
        return connIdType;
    }

    @Override
    public Class<? extends P> primaryWireType() {
        return impl.primaryWireType();
    }

    @Override
    public P toWireValue(O value) throws IllegalArgumentException {
        return impl.toWireValue(toDelegate.apply(value));
    }

    @Override
    public O toConnIdValue(P value) throws IllegalArgumentException {
        return toOverride.apply(impl.toConnIdValue(value));
    }

}