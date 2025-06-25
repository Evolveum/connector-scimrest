/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scim.rest.schema;

import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class MappedObjectClass {

    private final Map<String, MappedAttribute> nativeAttributes;
    private final Map<String, MappedAttribute> connIdAttributes;
    ObjectClass clazz;
    ObjectClassInfo connId;

    Map<String, MappedAttribute> attributes = new HashMap<>();

    public MappedObjectClass(ObjectClassInfo connId, Map<String, MappedAttribute> nativeAttrs, Map<String, MappedAttribute> connIdAttrs) {
        this.connId = connId;
        this.clazz = new ObjectClass(connId.getType());
        this.nativeAttributes = nativeAttrs;
        this.connIdAttributes = connIdAttrs;
    }

    public ConnectorObjectBuilder newObjectBuilder() {
        ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
        builder.setObjectClass(clazz);
        return builder;
    }

    public Collection<MappedAttribute> attributes() {
        return nativeAttributes.values();
    }

    public ObjectClassInfo connId() {
        return connId;
    }

    public ObjectClass objectClass() {
        return clazz;
    }

    public MappedAttribute attributeFromProtocolName(String protocolName) {
        return nativeAttributes.get(protocolName);
    }

    public String name() {
        return objectClass().getObjectClassValue();
    }
}
