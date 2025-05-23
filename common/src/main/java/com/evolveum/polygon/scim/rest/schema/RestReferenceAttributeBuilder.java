package com.evolveum.polygon.scim.rest.schema;

import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.AttributeInfoBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObjectReference;
import org.identityconnectors.framework.common.objects.ObjectClass;

public class RestReferenceAttributeBuilder extends RestAttributeBuilder {


    private String referencedObjectClass;

    public static final AttributeInfo.RoleInReference SUBJECT = AttributeInfo.RoleInReference.SUBJECT;
    public static final AttributeInfo.RoleInReference OBJECT = AttributeInfo.RoleInReference.OBJECT;



    public RestReferenceAttributeBuilder(RestObjectClassBuilder restObjectClassBuilder, String name) {
        super(restObjectClassBuilder, name);
        this.connIdBuilder.setType(ConnectorObjectReference.class);
    }

    public RestReferenceAttributeBuilder objectClass(String objectClass) {
        this.referencedObjectClass = objectClass;
        this.connIdBuilder.setReferencedObjectClassName(objectClass);
        return this;
    }

    public RestReferenceAttributeBuilder subtype(String subtype) {
        this.connIdBuilder.setSubtype(subtype);
        return this;
    }

    public RestReferenceAttributeBuilder role(String role) {
        this.connIdBuilder.setRoleInReference(role);
        return this;
    }
    public RestReferenceAttributeBuilder role(AttributeInfo.RoleInReference role) {
        this.connIdBuilder.setRoleInReference(role.toString());
        return this;
    }


}
