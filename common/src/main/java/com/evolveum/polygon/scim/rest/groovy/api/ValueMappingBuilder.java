/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scim.rest.groovy.api;

import groovy.lang.Closure;

import java.util.function.Function;

public interface ValueMappingBuilder<C,P> {

    ValueMappingBuilder<C,P> deserialize(Closure<C> closure);

    ValueMappingBuilder<C,P> serialize(Closure<P> closure);

}
