/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.schema;

import com.evolveum.polygon.conndev.annotations.Yaml;
import com.evolveum.polygon.conndev.build.api.AttributeResolverBuilder;
import com.evolveum.polygon.conndev.build.api.ReferenceAttributeBuilder;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import com.evolveum.polygon.conndev.concepts.SourceLocation;
import com.evolveum.polygon.scimrest.groovy.api.RestAttributeBuilder;
import com.evolveum.polygon.scimrest.groovy.api.RestReferenceAttributeBuilder;
import com.evolveum.polygon.scimrest.groovy.api.RestRelationshipBuilder;
import groovy.lang.Closure;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.objects.ConnectorObjectReference;
import org.identityconnectors.framework.common.objects.ObjectClass;

public class ParticipantBuilder implements RestRelationshipBuilder.Participant {

    private final RestObjectClassDefinitionBuilder objectClass;
    private final RelationshipBuilderImpl parent;

    /** The script location of the participant's {@code subject(...)} / {@code object(...)} declaration. */
    private final SourceLocation location;

    private AttributeBuilder attribute;
    /** Unused by scimrest today (no .schema.groovy script calls owner(...)); tracked only to satisfy the interface. */
    private boolean owner = false;

    public ParticipantBuilder(RelationshipBuilderImpl relationshipBuilder, RestObjectClassDefinitionBuilder targetClass,
            SourceLocation location) {
        this.parent = relationshipBuilder;
        this.objectClass = targetClass;
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }

    /**
     * Returns the script location of the participant declaration (never {@code null}).
     *
     * @return the captured location, or {@link SourceLocation#UNKNOWN}
     */
    public SourceLocation location() {
        return location;
    }

    AttributeBuilder attribute() {
        return attribute;
    }

    @Override
    public AttributeBuilder attribute(String name) {
        if (attribute == null) {
            attribute = new AttributeBuilder(objectClass.attribute(name));
            attribute.delegate.connId().type(ConnectorObjectReference.class);
            attribute.delegate.subtype(parent.name);
        }
        return attribute;
    }

    @Override
    public RestReferenceAttributeBuilder attribute(String name, Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, attribute(name));
    }

    @Override
    public boolean owner() {
        return owner;
    }

    @Override
    public RestReferenceAttributeBuilder owner(boolean owner) {
        this.owner = owner;
        if (attribute == null) {
            throw new ConfigurationException(ownerRequiresAttributeMessage());
        }
        return attribute;
    }

    /**
     * Builds the configuration error message for {@link #owner(boolean)} called before the
     * participant's reference attribute is declared.
     *
     * @return the message, including the participant's source location when known
     */
    private String ownerRequiresAttributeMessage() {
        return "owner() requires the reference attribute to be declared first: declare attribute('<name>') "
                + "in the " + objectClass.objectClass().getObjectClassValue() + " participant before owner(...)"
                + (location != SourceLocation.UNKNOWN ? " [at " + location + "]" : "");
    }

    public ObjectClass objectClass() {
        return objectClass.objectClass();
    }

    static class AttributeBuilder implements RestReferenceAttributeBuilder,
            ReferenceAttributeBuilder.Delegator<RestReferenceAttributeBuilder, RestAttributeBuilder<RestReferenceAttributeBuilder>, RestAttributeDefinition> {

        private final RestAttributeBuilderImpl delegate;

        public AttributeBuilder(RestAttributeBuilderImpl delegate) {
            this.delegate = delegate;
        }

        @Override
        public RestReferenceAttributeBuilder delegate() {
            return delegate;
        }

        @Override
        public String name() {
            return delegate.name();
        }

        @Override
        public RestReferenceAttributeBuilder complexType(DefinitionValue<ObjectClass> objectClass) {
            return delegate.complexType(objectClass);
        }

        @Override
        public RestReferenceAttributeBuilder emulated(DefinitionValue<Boolean> emulated) {
            return delegate.emulated(emulated);
        }

        @Override
        public RestReferenceAttributeBuilder protocolName(DefinitionValue<String> protocolName) {
            return delegate.protocolName(protocolName);
        }

        @Override
        public RestReferenceAttributeBuilder remoteName(DefinitionValue<String> remoteName) {
            return delegate.remoteName(remoteName);
        }

        @Override
        public RestReferenceAttributeBuilder nativeType(String nativeType) {
            delegate.nativeType(nativeType);
            return this;
        }

        @Override
        public ScimMapping scim() {
            return delegate.scim();
        }

        @Override
        public ScimMapping scim(Closure<?> closure) {
            return delegate.scim(closure);
        }

        @Yaml.Sub
        @Override
        public Lookup lookup() {
            throw new UnsupportedOperationException("lookup is not supported on this attribute builder");
        }

        @Override
        public Lookup lookup(Closure<?> closure) {
            return lookup();
        }

        @Override
        public AttributeResolverBuilder resolver(Closure<?> closure) {
            return delegate.resolver(closure);
        }
    }
}
