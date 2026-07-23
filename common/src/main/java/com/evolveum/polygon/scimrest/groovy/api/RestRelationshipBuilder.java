/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy.api;

import com.evolveum.polygon.conndev.build.api.RelationshipBuilder;
import com.evolveum.polygon.scimrest.schema.MappedAttribute;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public interface RestRelationshipBuilder extends RelationshipBuilder<RestReferenceAttributeBuilder, MappedAttribute> {

    /**
     * Declares object class as a subject of this relationship
     *
     * @param objectClass Object Class of subject of this relationship (semantic owner of relation)
     * @param closure Groovy closure which configures subject-part of relation
     * @return Builder which allows to customize subject part of relationship
     */
    @Override
    Participant subject(String objectClass, @DelegatesTo(value = Participant.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure);

    /**
     * Declares an object class as a participant of this relationship.
     *
     * @param objectClass Object class of the object.
     * @param closure Groovy closure to configure the participant part of the relationship.
     * @return Builder for customizing the participant part of the relationship.
     */
    @Override
    Participant object(String objectClass, @DelegatesTo(value = Participant.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure);

    interface Participant extends RelationshipBuilder.Participant<RestReferenceAttributeBuilder, MappedAttribute> {

        @Override
        RestReferenceAttributeBuilder attribute(String name);

        @Override
        RestReferenceAttributeBuilder attribute(String name, @DelegatesTo(value = RestReferenceAttributeBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure);
    }
}
