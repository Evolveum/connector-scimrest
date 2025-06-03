package com.evolveum.polygon.scim.rest.groovy.api;

import com.evolveum.polygon.scim.rest.schema.RestReferenceAttributeBuilderImpl;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public interface RestRelationshipBuilder {


    Subject subject(String name, @DelegatesTo(value = Subject.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure);

    Object object(String name, @DelegatesTo(value = Object.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure);

    interface Subject extends Participant {

    }

    interface Object extends Participant{
    }

    interface Participant {

        RestReferenceAttributeBuilderImpl attribute(String name);

        RestReferenceAttributeBuilderImpl attribute(String name, @DelegatesTo(value = RestReferenceAttributeBuilderImpl.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure);

    }
}
