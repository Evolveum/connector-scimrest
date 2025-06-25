/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy.api;

import groovy.lang.Closure;
import org.identityconnectors.framework.common.objects.AttributeInfo;

public interface RestReferenceAttributeDelegator extends RestReferenceAttributeBuilder {

    RestReferenceAttributeBuilder delegate();

    @Override
    default RestReferenceAttributeBuilder objectClass(String objectClass) {
        return delegate().objectClass(objectClass);
    }

    @Override
    default RestReferenceAttributeBuilder subtype(String subtype) {
        return delegate().subtype(subtype);
    }

    @Override
    default RestReferenceAttributeBuilder role(String role) {
        return delegate().role(role);
    }

    @Override
    default RestReferenceAttributeBuilder role(AttributeInfo.RoleInReference role) {
        return delegate().role(role);
    }

    @Override
    default RestAttributeBuilder readable(boolean readable) {
        return delegate().readable(readable);
    }

    @Override
    default RestAttributeBuilder required(boolean required) {
        return delegate().required(required);
    }

    @Override
    default RestAttributeBuilder returnedByDefault(boolean returnedByDefault) {
        return delegate().returnedByDefault(returnedByDefault);
    }

    @Override
    default RestAttributeBuilder multiValued(boolean multiValued) {
        return delegate().multiValued(multiValued);
    }

    @Override
    default RestAttributeBuilder creatable(boolean creatable) {
        return delegate().creatable(creatable);
    }

    @Override
    default RestAttributeBuilder updateable(boolean updateable) {
        return delegate().updateable(updateable);
    }

    @Override
    default void emulated(boolean emulated) {
        delegate().emulated(emulated);
    }

    @Override
    default JsonMapping json() {
        return delegate().json();
    }

    default JsonMapping json(Closure<?> closure) {
        return delegate().json(closure);
    }

    @Override
    default ScimMapping scim() {
        return delegate().scim();
    }

    @Override
    default ScimMapping scim(Closure<?> closure) {
        return delegate().scim(closure);
    }

    @Override
    default ConnIdMapping connId() {
        return delegate().connId();
    }

    @Override
    default RestAttributeBuilder protocolName(String protocolName) {
        return delegate().protocolName(protocolName);
    }

    @Override
    default RestAttributeBuilder remoteName(String remoteName) {
        return delegate().remoteName(remoteName);
    }

    @Override
    default RestAttributeBuilder jsonType(String jsonType) {
        return delegate().jsonType(jsonType);
    }

    @Override
    default RestAttributeBuilder openApiFormat(String openapiFormat) {
        return delegate().openApiFormat(openapiFormat);
    }


}
