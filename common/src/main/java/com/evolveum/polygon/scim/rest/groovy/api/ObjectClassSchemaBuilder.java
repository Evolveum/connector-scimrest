package com.evolveum.polygon.scim.rest.groovy.api;

import com.evolveum.polygon.scim.rest.schema.RestAttributeBuilderImpl;
import com.evolveum.polygon.scim.rest.schema.RestReferenceAttributeBuilderImpl;
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

    }


}
