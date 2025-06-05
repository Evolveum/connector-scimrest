package com.evolveum.polygon.scim.rest.schema;

import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.ConnectorObjectReference;

public class MappedReferenceAttributeBuilderImpl extends MappedAttributeBuilderImpl implements com.evolveum.polygon.scim.rest.groovy.api.RestReferenceAttributeBuilder {


    private String referencedObjectClass;




    public MappedReferenceAttributeBuilderImpl(MappedObjectClassBuilder restObjectClassBuilder, String name) {
        super(restObjectClassBuilder, name);
        this.connIdBuilder.setType(ConnectorObjectReference.class);
    }

    @Override
    public MappedReferenceAttributeBuilderImpl objectClass(String objectClass) {
        this.referencedObjectClass = objectClass;
        this.connIdBuilder.setReferencedObjectClassName(objectClass);
        return this;
    }

    @Override
    public MappedReferenceAttributeBuilderImpl subtype(String subtype) {
        this.connIdBuilder.setSubtype(subtype);
        return this;
    }

    @Override
    public MappedReferenceAttributeBuilderImpl role(String role) {
        this.connIdBuilder.setRoleInReference(role);
        return this;
    }

    @Override
    public MappedReferenceAttributeBuilderImpl role(AttributeInfo.RoleInReference role) {
        this.connIdBuilder.setRoleInReference(role.toString());
        return this;
    }


}
