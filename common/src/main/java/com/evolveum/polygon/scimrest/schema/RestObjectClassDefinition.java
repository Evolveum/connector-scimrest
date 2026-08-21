/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.schema;

import com.evolveum.polygon.conndev.dev.ConnDevObjectClass;
import com.evolveum.polygon.conndev.schema.BaseObjectClassDefinition;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RestObjectClassDefinition extends BaseObjectClassDefinition<RestAttributeDefinition> {

    private final ObjectClassScimMapping scim;

    public RestObjectClassDefinition(ObjectClassInfo connId, Map<String, RestAttributeDefinition> nativeAttrs, Map<String, RestAttributeDefinition> connIdAttrs) {
        this(connId, nativeAttrs, connIdAttrs, null);
    }

    public RestObjectClassDefinition(ObjectClassInfo connId, Map<String, RestAttributeDefinition> nativeAttrs, Map<String, RestAttributeDefinition> connIdAttrs,
            ObjectClassScimMapping scim) {
        super(connId, nativeAttrs, connIdAttrs);
        this.scim = scim;
    }

    @Override
    public void contribute(ConnDevObjectClass target) {
        if (scim != null) {
            target.protocolSpecific("scim", scim.exportAttributes());
        }
    }

    public record ObjectClassScimMapping(String name, String schemaUri) {

        List<Attribute> exportAttributes() {
            var attributes = new ArrayList<Attribute>();
            if (name != null) {
                attributes.add(AttributeBuilder.build("name", name));
            }
            if (schemaUri != null) {
                attributes.add(AttributeBuilder.build("schemaUri", schemaUri));
            }
            return attributes;
        }
    }
}
