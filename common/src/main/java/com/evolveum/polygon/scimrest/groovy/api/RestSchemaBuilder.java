/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy.api;

import com.evolveum.polygon.conndev.build.api.SchemaBuilder;
import com.evolveum.polygon.scimrest.groovy.Script;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public interface RestSchemaBuilder extends SchemaBuilder<RestSchemaBuilder, RestObjectClassSchemaBuilder> {

    @Override
    RestObjectClassSchemaBuilder objectClass(String name, @DelegatesTo(RestObjectClassSchemaBuilder.class) @Script.Initialization Closure<?> closure);

    @Override
    RestRelationshipBuilder relationship(String name, @DelegatesTo(RestRelationshipBuilder.class) @Script.Initialization Closure<?> closure);

}
