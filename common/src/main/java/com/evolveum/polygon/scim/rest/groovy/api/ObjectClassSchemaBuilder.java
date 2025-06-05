package com.evolveum.polygon.scim.rest.groovy.api;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public interface ObjectClassSchemaBuilder {

    ObjectClassSchemaBuilder description(String description);

    RestAttributeBuilder attribute(String name);

    RestReferenceAttributeBuilder reference(String name);

    RestAttributeBuilder attribute(String name, @DelegatesTo(RestAttributeBuilder.class) Closure<?> closure);

    RestReferenceAttributeBuilder reference(String name, @DelegatesTo(RestReferenceAttributeBuilder.class) Closure<?> closure);

    ScimMapping scim();

    ScimMapping scim(@DelegatesTo(ScimMapping.class) Closure<?> closure);

    interface ScimMapping {

        ScimMapping extension(String alias, String namespace);

        String schemaUri();

        /**
         * Name of SCIM Resource which this object class represents
         * @return name of SCIM Resource
         */
        String name();

        void name(String name);

        boolean isOnlyExplicitlyListed();

        ScimMapping onlyExplicitlyListed(boolean value);
    }


}
