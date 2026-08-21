/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.schema.strategy;

import com.evolveum.polygon.scimrest.impl.scim.ScimResourceContext;
import com.evolveum.polygon.scimrest.schema.ScimAttributeMappingRule;
import com.evolveum.polygon.scimrest.schema.ScimMappingAction;
import com.unboundid.scim2.common.types.AttributeDefinition;

import static com.evolveum.polygon.conndev.concepts.DefinitionValue.detected;

/**
 * Transfers SCIM attribute metadata (description, required, multi-valued, returned)
 * to the corresponding ConnId attribute flags.
 */
public class ScimMetadataToConnIdRule implements ScimAttributeMappingRule {

    @Override
    public boolean checkIfApplicable(ScimResourceContext resource, AttributeDefinition attrDef) {
        return attrDef != null;
    }

    @Override
    public ScimMappingAction createAction(ScimResourceContext resource, AttributeDefinition attrDef) {
        return ScimMappingAction.attributeSpecific(attrDef.getName(), attribute -> {
            attribute.nativeType(attrDef.getType().getName());
            if (attrDef.getDescription() != null) {
                attribute.connId().description(detected(attrDef.getDescription()));
            }
            attribute.connId().required(detected(attrDef.isRequired()));
            attribute.connId().multiValued(detected(attrDef.isMultiValued()));
            attribute.connId().returnedByDefault(detected(
                    AttributeDefinition.Returned.DEFAULT.equals(attrDef.getReturned())));
        });
    }
}
