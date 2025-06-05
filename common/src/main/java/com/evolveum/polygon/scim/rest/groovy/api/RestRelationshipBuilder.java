package com.evolveum.polygon.scim.rest.groovy.api;

import com.evolveum.polygon.scim.rest.schema.MappedReferenceAttributeBuilderImpl;
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

        MappedReferenceAttributeBuilderImpl attribute(String name);

        Reference attribute(String name, @DelegatesTo(value = Reference.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure);

    }

    interface Reference extends RestReferenceAttributeBuilder {

        /**
         * Adds an attribute resolver with custom behavior defined.
         *
         * The attribute will be marked as {@link #emulated(boolean)} true.
         * The values for attribute will be provided by defined attribute resolver.
         *
         *
         * @param closure The closure defining custom behavior for the attribute resolver.
         * @return A new instance of {@link AttributeResolver} configured according to the closure's specifications.
         */
        AttributeResolverBuilder resolver(@DelegatesTo(value = AttributeResolverBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure);
    }
}
