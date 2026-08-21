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
 * Maps SCIM mutability semantics to Connector Framework CRUD flags.
 * <ul>
 *   <li>IMMUTABLE   &rarr; readable=true, creatable=true, updatable=false</li>
 *   <li>READ_ONLY   &rarr; readable=true, creatable=false, updatable=false</li>
 *   <li>READ_WRITE  &rarr; readable=true, creatable=true, updatable=true</li>
 *   <li>WRITE_ONLY  &rarr; readable=false, creatable=true, updatable=true</li>
 * </ul>
 */
public class ScimMutabilityToCrudRule implements ScimAttributeMappingRule {

    @Override
    public boolean checkIfApplicable(ScimResourceContext resource, AttributeDefinition attrDef) {
        return attrDef != null && attrDef.getMutability() != null;
    }

    @Override
    public ScimMappingAction createAction(ScimResourceContext resource, AttributeDefinition attrDef) {
        return ScimMappingAction.attributeSpecific(attrDef.getName(), attribute -> {
            switch (attrDef.getMutability()) {
                case IMMUTABLE:
                    attribute.connId().readable(detected(true)).creatable(detected(true)).updatable(detected(false));
                    break;
                case READ_ONLY:
                    attribute.connId().readable(detected(true)).creatable(detected(false)).updatable(detected(false));
                    break;
                case READ_WRITE:
                    attribute.connId().readable(detected(true)).creatable(detected(true)).updatable(detected(true));
                    break;
                case WRITE_ONLY:
                    attribute.connId().readable(detected(false)).creatable(detected(true)).updatable(detected(true));
                    break;
            }
        });
    }
}
