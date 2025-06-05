package com.evolveum.polygon.scim.rest.groovy.api;

import com.evolveum.polygon.scim.rest.schema.MappedObjectClassBuilder;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public interface SchemaBuilder {


    MappedObjectClassBuilder objectClass(String name);

    MappedObjectClassBuilder objectClass(String name, @DelegatesTo(MappedObjectClassBuilder.class) Closure<?> closure);

    RestRelationshipBuilder relationship(String name, @DelegatesTo(RestRelationshipBuilder.class) Closure<?> closure);

}
