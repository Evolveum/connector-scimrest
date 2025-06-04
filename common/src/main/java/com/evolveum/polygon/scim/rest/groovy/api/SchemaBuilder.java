package com.evolveum.polygon.scim.rest.groovy.api;

import com.evolveum.polygon.scim.rest.schema.RestObjectClassBuilder;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public interface SchemaBuilder {


    RestObjectClassBuilder objectClass(String name);

    RestObjectClassBuilder objectClass(String name, @DelegatesTo(RestObjectClassBuilder.class) Closure<?> closure);

    RestRelationshipBuilder relationship(String name, @DelegatesTo(RestRelationshipBuilder.class) Closure<?> closure);

}
