/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy.api;

import org.identityconnectors.framework.common.objects.AttributeInfo;

public interface RestReferenceAttributeBuilder extends RestAttributeBuilder {

    AttributeInfo.RoleInReference SUBJECT = AttributeInfo.RoleInReference.SUBJECT;
    AttributeInfo.RoleInReference OBJECT = AttributeInfo.RoleInReference.OBJECT;

    RestReferenceAttributeBuilder objectClass(String objectClass);

    RestReferenceAttributeBuilder subtype(String subtype);

    RestReferenceAttributeBuilder role(String role);

    RestReferenceAttributeBuilder role(AttributeInfo.RoleInReference role);
}
