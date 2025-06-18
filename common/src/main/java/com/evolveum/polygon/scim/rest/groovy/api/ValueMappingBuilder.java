package com.evolveum.polygon.scim.rest.groovy.api;

import groovy.lang.Closure;

import java.util.function.Function;

public interface ValueMappingBuilder<C,P> {

    ValueMappingBuilder<C,P> deserialize(Closure<C> closure);

    ValueMappingBuilder<C,P> serialize(Closure<P> closure);

}
