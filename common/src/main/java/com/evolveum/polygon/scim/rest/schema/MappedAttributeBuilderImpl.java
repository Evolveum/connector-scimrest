package com.evolveum.polygon.scim.rest.schema;

import org.identityconnectors.framework.common.objects.AttributeInfo;

public class MappedAttributeBuilderImpl extends MappedBasicAttributeBuilderImpl implements com.evolveum.polygon.scim.rest.groovy.api.RestReferenceAttributeBuilder {

    private String referencedObjectClass;

    public MappedAttributeBuilderImpl(MappedObjectClassBuilder restObjectClassBuilder, String name) {
        super(restObjectClassBuilder, name);
    }

    @Override
    public MappedAttributeBuilderImpl objectClass(String objectClass) {
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
        this.connIdBuilder.setRoleInReference(role);
        return this;
    }

    @Override
    public MappedAttributeBuilderImpl role(AttributeInfo.RoleInReference role) {
        this.connIdBuilder.setRoleInReference(role.toString());
        return this;
    }


}
