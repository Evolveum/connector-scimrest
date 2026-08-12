/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.schema;

import com.evolveum.polygon.conndev.dev.ConnDevAttribute;
import com.evolveum.polygon.conndev.schema.BaseAttributeDefinition;
import org.identityconnectors.framework.common.objects.AttributeBuilder;

import java.util.List;

public class RestAttributeDefinition extends BaseAttributeDefinition {

    private final String nativeType;

    public RestAttributeDefinition(RestAttributeBuilderImpl builder) {
        super(builder);
        this.nativeType = builder.nativeType;
    }

    @Override
    public String nativeType() {
        return nativeType;
    }

    public ScimAttributeMapping scim() {
        return mapping(ScimAttributeMapping.class);
    }

    @Override
    public void contribute(ConnDevAttribute target) {
        var scim = scim();
        if (scim != null && scim.path() != null) {
            target.protocolSpecific("scim", List.of(AttributeBuilder.build("path", scim.path().toString())));
        }
    }
}
