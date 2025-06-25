/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.schema;

import com.evolveum.polygon.scimrest.groovy.GroovyClosures;
import com.evolveum.polygon.scimrest.groovy.api.AttributeResolverBuilder;
import com.evolveum.polygon.scimrest.groovy.api.RestReferenceAttributeBuilder;
import com.evolveum.polygon.scimrest.groovy.api.RestReferenceAttributeDelegator;
import com.evolveum.polygon.scimrest.groovy.api.RestRelationshipBuilder;
import groovy.lang.Closure;
import org.identityconnectors.framework.common.objects.ConnectorObjectReference;

public class ParticipantBuilder implements RestRelationshipBuilder.Participant {

    private final MappedObjectClassBuilder objectClass;
    private final RelationshipBuilderImpl parent;

    private AttributeBuilder attribute;

    public ParticipantBuilder(RelationshipBuilderImpl relationshipBuilder, MappedObjectClassBuilder targetClass) {
        this.parent = relationshipBuilder;
        this.objectClass = targetClass;
    }

    AttributeBuilder attribute() {
        return attribute;
    }

    @Override
    public AttributeBuilder attribute(String name) {
        if (attribute == null) {
            attribute = new AttributeBuilder(objectClass.attribute(name));
            attribute.delegate.connIdBuilder.setType(ConnectorObjectReference.class);
            attribute.delegate.subtype(parent.name);
        }
        return attribute;
    }

    @Override
    public RestRelationshipBuilder.Reference attribute(String name, Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, attribute(name));
    }

    public String objectClass() {
        return objectClass.name();
    }

    static class AttributeBuilder implements RestRelationshipBuilder.Reference, RestReferenceAttributeDelegator {

        private final MappedAttributeBuilderImpl delegate;

        public AttributeBuilder(MappedAttributeBuilderImpl delegate) {
            this.delegate = delegate;
        }

        @Override
        public RestReferenceAttributeBuilder delegate() {
            return delegate;
        }

        @Override
        public AttributeResolverBuilder resolver(Closure<?> closure) {
            return delegate.resolver(closure);
        }
    }
}
