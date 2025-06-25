/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy.api;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public interface RestRelationshipBuilder {


    /**
     * Declares object class as a subject of this relationship
     *
     * @param objectClass Object Class of subject of this relationship (semantic owner of relation)
     * @param closure Groovy closure which configures subject-part of relation
     * @return Builder which allows to customize subject part of relationship
     */
    Participant subject(String objectClass, @DelegatesTo(value = Participant.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure);


    /**
     * Declares an object class as a participant of this relationship.
     *
     * @param objectClass Object class of the object.
     * @param closure Groovy closure to configure the participant part of the relationship.
     * @return Builder for customizing the participant part of the relationship.
     */
    Participant object(String objectClass, @DelegatesTo(value = Participant.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure);



    interface Participant {

        Reference attribute(String name);

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
