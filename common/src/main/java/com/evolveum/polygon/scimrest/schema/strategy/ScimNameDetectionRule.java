/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.schema.strategy;

import com.evolveum.polygon.scimrest.groovy.api.RestAttributeBuilder;
import com.evolveum.polygon.scimrest.groovy.api.RestObjectClassSchemaBuilder;
import com.evolveum.polygon.scimrest.groovy.api.RestReferenceAttributeBuilder;
import com.evolveum.polygon.scimrest.impl.scim.ScimCoreSchemaUrns;
import com.evolveum.polygon.scimrest.schema.ScimAttributeMappingRule;
import com.evolveum.polygon.scimrest.schema.ScimMappingAction;
import org.identityconnectors.framework.common.objects.Name;

/**
 * Maps the standard SCIM name attribute to the Connector Framework {@link Name}.
 * <ul>
 *   <li>User schema    &rarr; {@code userName}  mapping to {@link Name}</li>
 *   <li>Group schema   &rarr; {@code displayName} mapping to {@link Name}</li>
 * </ul>
 */
public class ScimNameDetectionRule implements ScimAttributeMappingRule {

    private static final String USER_NAME_ATTR = "userName";
    private static final String GROUP_NAME_ATTR = "displayName";

    @Override
    public boolean checkIfApplicable(ScimAttributeMappingRule.Context context, RestObjectClassSchemaBuilder objectClass, RestAttributeBuilder<RestReferenceAttributeBuilder> attribute) {
        var resource = context.resource();
        var attrDef = context.attribute();
        if (attrDef == null || resource.primarySchema() == null) {
            return false;
        }
        String schemaId = resource.primarySchema().getId();
        String attrName = attrDef.getName();
        return (ScimCoreSchemaUrns.USER_SCHEMA_URN.equals(schemaId) && USER_NAME_ATTR.equals(attrName))
                || (ScimCoreSchemaUrns.GROUP_SCHEMA_URN.equals(schemaId) && GROUP_NAME_ATTR.equals(attrName));
    }

    @Override
    public ScimMappingAction createAction(ScimAttributeMappingRule.Context context) {
        return new ScimMappingAction() {
            @Override
            public void applyToAttribute(RestAttributeBuilder<RestReferenceAttributeBuilder> attribute) {
                attribute.connId().name(Name.NAME);
            }
        };
    }
}
