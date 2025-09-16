/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy.api;

import com.evolveum.polygon.scimrest.schema.MappedObjectClassBuilder;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public interface SchemaBuilder {


    ObjectClassSchemaBuilder objectClass(String name);

    ObjectClassSchemaBuilder objectClass(String name, @DelegatesTo(ObjectClassSchemaBuilder.class) Closure<?> closure);

    RestRelationshipBuilder relationship(String name, @DelegatesTo(RestRelationshipBuilder.class) Closure<?> closure);

}
