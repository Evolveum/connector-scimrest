/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy.api;

import com.evolveum.polygon.conndev.concepts.DefinitionValue;
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
    default RestAttributeBuilder readable(DefinitionValue<Boolean> readable) {
        return delegate().readable(readable);
    }

    @Override
    default RestAttributeBuilder required(DefinitionValue<Boolean> required) {
        return delegate().required(required);
    }

    @Override
    default RestAttributeBuilder complexType(DefinitionValue<String> objectClass) {
        return delegate().complexType(objectClass);
    }

    @Override
    default RestAttributeBuilder returnedByDefault(DefinitionValue<Boolean> returnedByDefault) {
        return delegate().returnedByDefault(returnedByDefault);
    }

    @Override
    default RestAttributeBuilder multiValued(DefinitionValue<Boolean> multiValued) {
        return delegate().multiValued(multiValued);
    }

    @Override
    default RestAttributeBuilder creatable(DefinitionValue<Boolean> creatable) {
        return delegate().creatable(creatable);
    }

    @Override
    default RestAttributeBuilder updateable(DefinitionValue<Boolean> updateable) {
        return delegate().updateable(updateable);
    }

    @Override
    default RestAttributeBuilder description(DefinitionValue<String> description) {
        return delegate().description(description);
    }

    @Override
    default RestAttributeBuilder emulated(DefinitionValue<Boolean> emulated) {
        return delegate().emulated(emulated);
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
    default RestAttributeBuilder protocolName(DefinitionValue<String> protocolName) {
        return delegate().protocolName(protocolName);
    }

    @Override
    default RestAttributeBuilder remoteName(DefinitionValue<String> remoteName) {
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
