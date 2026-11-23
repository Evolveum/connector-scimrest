/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.schema.strategy;

import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.json.OpenApiValueMapping;
import com.evolveum.polygon.conndev.schema.ValueTypeOverrideMapping;
import com.evolveum.polygon.scimrest.groovy.api.RestAttributeBuilder;
import com.evolveum.polygon.scimrest.groovy.api.RestObjectClassSchemaBuilder;
import com.evolveum.polygon.scimrest.groovy.api.RestReferenceAttributeBuilder;
import com.evolveum.polygon.scimrest.impl.scim.ScimCoreSchemaUrns;
import com.evolveum.polygon.scimrest.schema.ScimAttributeMappingRule;
import com.evolveum.polygon.scimrest.schema.ScimMappingAction;
import com.unboundid.scim2.common.types.AttributeDefinition;
import org.identityconnectors.common.security.GuardedString;

/**
 * Maps the standard SCIM {@code password} attribute (RFC 7643, section 3.1.2) of the User
 * schema to the Connector Framework {@link GuardedString}, so a discovered password is
 * handled as a secret — and masked in operation logs — out of the box, without the
 * connector author having to declare {@code connId { type GuardedString }} for a well-known
 * attribute.
 *
 * <p>The rule does not set the ConnId type directly; it gives the SCIM value mapping a
 * {@link GuardedString} type, so the type resolution step sees
 * {@code GuardedString} as the protocol's suggestion. An explicit
 * {@code connId { type ... }} declaration by the connector author still takes precedence
 * over this detected default.
 */
public class ScimPasswordGuardedStringRule implements ScimAttributeMappingRule {

    private static final String PASSWORD_ATTR = "password";

    @Override
    public boolean checkIfApplicable(ScimAttributeMappingRule.Context context,
                                     RestObjectClassSchemaBuilder objectClass,
                                     RestAttributeBuilder<RestReferenceAttributeBuilder> attribute) {
        var resource = context.resource();
        var attrDef = context.attribute();
        if (attrDef == null || resource.primarySchema() == null) {
            return false;
        }
        return ScimCoreSchemaUrns.USER_SCHEMA_URN.equals(resource.primarySchema().getId())
                && PASSWORD_ATTR.equals(attrDef.getName())
                && AttributeDefinition.Type.STRING.equals(attrDef.getType());
    }

    @Override
    public ScimMappingAction createAction(ScimAttributeMappingRule.Context context) {
        return new ScimMappingAction() {
            @Override
            public void applyToAttribute(RestAttributeBuilder<RestReferenceAttributeBuilder> attribute) {
                attribute.scim().implementation(
                        ValueTypeOverrideMapping.of(GuardedString.class, OpenApiValueMapping.from("string", null)));
            }
        };
    }
}
