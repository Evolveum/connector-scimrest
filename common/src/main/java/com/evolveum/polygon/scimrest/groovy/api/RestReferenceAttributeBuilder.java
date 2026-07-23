/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy.api;

import com.evolveum.polygon.conndev.build.api.RelationshipBuilder;
import com.evolveum.polygon.scimrest.schema.MappedAttribute;

/**
 * Extends {@code RelationshipBuilder.Reference} (not just plain {@code ReferenceAttributeBuilder}) so
 * every reference attribute already satisfies conndev's relationship-participant contract
 * ({@code resolver(Closure)}) — {@code MappedAttributeBuilderImpl} already implements it
 * unconditionally, so this adds no new burden.
 */
public interface RestReferenceAttributeBuilder extends RestAttributeBuilder<RestReferenceAttributeBuilder>,
        RelationshipBuilder.Reference<RestReferenceAttributeBuilder, RestAttributeBuilder<RestReferenceAttributeBuilder>, MappedAttribute> {
}
