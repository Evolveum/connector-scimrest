/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.schema;

import com.evolveum.polygon.scimrest.Deferred;
import com.evolveum.polygon.scimrest.groovy.GroovyClosures;
import com.evolveum.polygon.scimrest.groovy.ScriptedSingleAttributeResolverBuilder;
import com.evolveum.polygon.scimrest.groovy.api.AttributeResolverBuilder;
import com.evolveum.polygon.scimrest.groovy.api.RestReferenceAttributeBuilder;
import groovy.lang.Closure;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.Uid;

public class MappedAttributeBuilderImpl extends MappedBasicAttributeBuilderImpl implements RestReferenceAttributeBuilder {

    public Deferred.Settable<MappedAttribute> deffered = Deferred.settable();
    private String referencedObjectClass;
    private boolean isReference = false;
    ScriptedSingleAttributeResolverBuilder resolverBuilder;

    public MappedAttributeBuilderImpl(MappedObjectClassBuilder restObjectClassBuilder, String name) {
        super(restObjectClassBuilder, name);
    }

    @Override
    public MappedAttributeBuilderImpl objectClass(String objectClass) {
        isReference = true;
        this.referencedObjectClass = objectClass;
        this.connIdBuilder.setReferencedObjectClassName(objectClass);
        return this;
    }

    @Override
    public MappedAttributeBuilderImpl subtype(String subtype) {
        this.connIdBuilder.setSubtype(subtype);
        return this;
    }

    @Override
    public MappedAttributeBuilderImpl role(String role) {
        this.isReference = true;
        this.connIdBuilder.setRoleInReference(role);
        return this;
    }

    @Override
    public MappedAttributeBuilderImpl role(AttributeInfo.RoleInReference role) {
        return role(role.toString());
    }


    public boolean isReference() {
        return isReference;
    }

    /**
     * Builds and returns a {@code RestAttribute} instance with the specified attributes.
     *
     * @return a new {@code RestAttribute} instance configured with the current settings
     */
    public MappedAttribute build() {
        // FIXME: Could this be part of ConnID schema contributor?
        if (Uid.NAME.equals(connIdName)) {
            connId().type(String.class);
        }
        return new MappedAttribute(this);
    }

    public AttributeResolverBuilder resolver(Closure<?> closure) {
        this.emulated = true;
        this.resolverBuilder = new ScriptedSingleAttributeResolverBuilder(objectClass.name(), deffered);
        GroovyClosures.callAndReturnDelegate(closure, resolverBuilder);
        return resolverBuilder;
    }
}
