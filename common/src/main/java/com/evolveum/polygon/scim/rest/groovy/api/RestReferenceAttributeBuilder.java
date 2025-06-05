package com.evolveum.polygon.scim.rest.groovy.api;

import org.identityconnectors.framework.common.objects.AttributeInfo;

public interface RestReferenceAttributeBuilder extends RestAttributeBuilder {

    AttributeInfo.RoleInReference SUBJECT = AttributeInfo.RoleInReference.SUBJECT;
    AttributeInfo.RoleInReference OBJECT = AttributeInfo.RoleInReference.OBJECT;

    RestReferenceAttributeBuilder objectClass(String objectClass);

    RestReferenceAttributeBuilder subtype(String subtype);

    RestReferenceAttributeBuilder role(String role);

    RestReferenceAttributeBuilder role(AttributeInfo.RoleInReference role);
}
