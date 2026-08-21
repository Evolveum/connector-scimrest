/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.schema.strategy;

import com.evolveum.polygon.conndev.json.OpenApiValueMapping;
import com.evolveum.polygon.scimrest.impl.scim.ScimResourceContext;
import com.evolveum.polygon.scimrest.schema.ScimAttributeMappingRule;
import com.evolveum.polygon.scimrest.schema.ScimMappingAction;
import com.unboundid.scim2.common.types.AttributeDefinition;

/**
 * Maps SCIM primitive attribute types to their JSON representation
 * and configures the corresponding {@link OpenApiValueMapping} implementation.
 * <p>
 * Special SCIM types (DATETIME, DECIMAL, BINARY) have no direct JSON equivalent.
 * Their values travel as JSON string/number; the original SCIM type is preserved
 * as the attribute's native type.
 */
public class ScimTypeToJsonTypeRule implements ScimAttributeMappingRule {

    @Override
    public boolean checkIfApplicable(ScimResourceContext resource, AttributeDefinition attrDef) {
        return attrDef != null;
    }

    @Override
    public ScimMappingAction createAction(ScimResourceContext resource, AttributeDefinition attrDef) {
        return ScimMappingAction.attributeSpecific(attrDef.getName(), attribute -> {
            switch (attrDef.getType()) {
                case DATETIME:
                    attribute.scim().type("string");
                    attribute.scim().implementation(OpenApiValueMapping.DateTime);
                    break;
                case DECIMAL:
                    attribute.scim().type("number");
                    attribute.scim().implementation(OpenApiValueMapping.Decimal);
                    break;
                case BINARY:
                    attribute.scim().type("binary");
                    attribute.scim().implementation(OpenApiValueMapping.Binary);
                    break;
                case REFERENCE:
                    attribute.scim().type("string");
                    break;
                default:
                    attribute.scim().type(jsonType(attrDef.getType()));
                    break;
            }
        });
    }

    private static String jsonType(AttributeDefinition.Type type) {
        return switch (type) {
            case DATETIME, REFERENCE -> "string";
            case DECIMAL -> "number";
            default -> type.getName();
        };
    }
}
