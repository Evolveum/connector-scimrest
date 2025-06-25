/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scim.rest.schema;

import com.evolveum.polygon.scim.rest.ValueMapping;
import com.evolveum.polygon.scim.rest.groovy.GroovyClosures;
import com.evolveum.polygon.scim.rest.groovy.api.ValueMappingBuilder;
import groovy.lang.Closure;

import java.util.function.Function;

public class ValueMappingBuilderImpl<C,P> implements ValueMappingBuilder<C,P> {

    private Function<P, C> deserialize;
    private Function<C, P> serialize;
    private final Class<C> connIdType;
    private final Class<P> protocolType;

    public ValueMappingBuilderImpl(Class<C> connIdType, Class<P> protocolType) {
        this.connIdType = connIdType;
        this.protocolType = protocolType;
    }

    @Override
    public ValueMappingBuilder<C,P> deserialize(Closure<C> closure) {
        this.deserialize = GroovyClosures.asFunction(closure);
        return this;
    }

    @Override
    public ValueMappingBuilder<C,P> serialize(Closure<P> closure) {
        this.serialize = GroovyClosures.asFunction(closure);
        return this;
    }

    public ValueMapping<C,P> build() {
        return new ValueMappingImpl<>(connIdType, protocolType, deserialize, serialize);
    }

    private record ValueMappingImpl<C,P>(Class<C> connIdType, Class<P> protocolType, Function<P,C> deserialize,
                                         Function<C, P> serialize) implements ValueMapping<C,P> {

        @Override
        public Class<C> connIdType() {
            return connIdType;
        }

        @Override
        public Class<? extends P> primaryWireType() {
            return protocolType;
        }

        @Override
        public P toWireValue(C value) throws IllegalArgumentException {
            return serialize.apply(value);
        }

        @Override
        public C toConnIdValue(P value) throws IllegalArgumentException {
            return deserialize.apply(value);
        }
    }
}
