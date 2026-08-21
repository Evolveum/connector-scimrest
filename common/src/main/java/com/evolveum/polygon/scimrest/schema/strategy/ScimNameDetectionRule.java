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
import org.identityconnectors.framework.common.objects.Name;

/**
 * Maps the standard SCIM name attribute to the Connector Framework {@link Name}.
 * <ul>
 *   <li>User schema    &rarr; {@code userName}  mapping to {@link Name}</li>
 *   <li>Group schema   &rarr; {@code displayName} mapping to {@link Name}</li>
 * </ul>
 */
public class ScimNameDetectionRule implements ScimAttributeMappingRule {

    private static final String USER_SCHEMA_URN = "urn:ietf:params:scim:schemas:core:2.0:User";
    private static final String GROUP_SCHEMA_URN = "urn:ietf:params:scim:schemas:core:2.0:Group";

    private static final String USER_NAME_ATTR = "userName";
    private static final String GROUP_NAME_ATTR = "displayName";

    @Override
    public boolean checkIfApplicable(ScimResourceContext resource, AttributeDefinition attrDef) {
        if (attrDef == null || resource.primarySchema() == null) {
            return false;
        }
        String schemaId = resource.primarySchema().getId();
        String attrName = attrDef.getName();
        return (USER_SCHEMA_URN.equals(schemaId) && USER_NAME_ATTR.equals(attrName))
                || (GROUP_SCHEMA_URN.equals(schemaId) && GROUP_NAME_ATTR.equals(attrName));
    }

    @Override
    public ScimMappingAction createAction(ScimResourceContext resource, AttributeDefinition attrDef) {
        return ScimMappingAction.attributeSpecific(attrDef.getName(), attribute -> {
            attribute.connId().name(Name.NAME);
        });
    }
}
