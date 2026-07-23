/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.schema;

import com.evolveum.polygon.conndev.schema.BaseObjectClassDefinition;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;

import java.util.Map;

public class MappedObjectClass extends BaseObjectClassDefinition<MappedAttribute> {

    public MappedObjectClass(ObjectClassInfo connId, Map<String, MappedAttribute> nativeAttrs, Map<String, MappedAttribute> connIdAttrs) {
        super(connId, nativeAttrs, connIdAttrs);
    }

    @Override
    public MappedAttribute attributeFromProtocolName(String protocolName) {
        return (MappedAttribute) super.attributeFromProtocolName(protocolName);
    }
}
