/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.schema.strategy;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.scimrest.impl.scim.ScimMemberToConnectorObjectReference;
import com.evolveum.polygon.scimrest.impl.scim.ScimGroupToConnectorObjectReference;
import com.evolveum.polygon.scimrest.impl.scim.ScimResourceContext;
import com.evolveum.polygon.scimrest.schema.ScimAttributeMappingRule;
import com.evolveum.polygon.scimrest.schema.ScimMappingAction;
import com.unboundid.scim2.common.types.AttributeDefinition;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.ConnectorObjectReference;
import org.identityconnectors.framework.common.objects.ObjectClass;

import java.util.Map;

/**
 * Detects standard SCIM membership reference attributes and maps them to
 * {@link ConnectorObjectReference} with the appropriate value mapping.
 * <ul>
 *   <li>User schema &rarr; {@code groups} (subject references groups)</li>
 *   <li>Group schema &rarr; {@code members} (object, referenced by user)</li>
 * </ul>
 */
public class ScimMembershipReferenceRule implements ScimAttributeMappingRule {

    private static final String USER_SCHEMA_URN = "urn:ietf:params:scim:schemas:core:2.0:User";
    private static final String GROUP_SCHEMA_URN = "urn:ietf:params:scim:schemas:core:2.0:Group";

    private static final String USER_GROUPS_ATTR = "groups";
    private static final String GROUP_MEMBERS_ATTR = "members";

    private final Map<String, String> resourceToObjectClass;
    private final Map<String, ScimResourceContext> objectClassToResource;
    private final ContextLookup contextLookup;

    public ScimMembershipReferenceRule(Map<String, String> resourceToObjectClass,
                                       Map<String, ScimResourceContext> objectClassToResource,
                                       ContextLookup contextLookup) {
        this.resourceToObjectClass = resourceToObjectClass;
        this.objectClassToResource = objectClassToResource;
        this.contextLookup = contextLookup;
    }

    @Override
    public boolean checkIfApplicable(ScimResourceContext resource, AttributeDefinition attrDef) {
        if (attrDef == null || resource.primarySchema() == null) {
            return false;
        }
        String schemaId = resource.primarySchema().getId();
        String attrName = attrDef.getName();
        return isMembershipReference(schemaId, attrName);
    }

    private static boolean isMembershipReference(String schemaId, String attrName) {
        return (USER_SCHEMA_URN.equals(schemaId) && USER_GROUPS_ATTR.equals(attrName))
                || (GROUP_SCHEMA_URN.equals(schemaId) && GROUP_MEMBERS_ATTR.equals(attrName));
    }

    @Override
    public ScimMappingAction createAction(ScimResourceContext resource, AttributeDefinition attrDef) {
        String schemaId = resource.primarySchema().getId();
        String attrName = attrDef.getName();

        if (USER_SCHEMA_URN.equals(schemaId) && USER_GROUPS_ATTR.equals(attrName)) {
            return createGroupMembershipAction();
        }
        return createUserGroupMembershipAction();
    }

    private ScimMappingAction createGroupMembershipAction() {
        return ScimMappingAction.attributeSpecific(USER_GROUPS_ATTR, attribute -> {
            var groupOc = resourceToObjectClass.get("Group");
            attribute.connId().type(ConnectorObjectReference.class);
            if (groupOc != null) {
                attribute.objectClass(groupOc);
            }
            attribute.subtype("_User_Group_Membership");
            attribute.role(AttributeInfo.RoleInReference.SUBJECT);
            attribute.scim().implementation(new ScimGroupToConnectorObjectReference(new ObjectClass(groupOc)));
        });
    }

    private ScimMappingAction createUserGroupMembershipAction() {
        return ScimMappingAction.attributeSpecific(GROUP_MEMBERS_ATTR, attribute -> {
            var userOc = resourceToObjectClass.get("User");
            attribute.connId().type(ConnectorObjectReference.class);
            if (userOc != null) {
                attribute.objectClass(userOc);
            }
            attribute.role(AttributeInfo.RoleInReference.OBJECT);
            attribute.subtype("_User_Group_Membership");
            attribute.scim().implementation(new ScimMemberToConnectorObjectReference(contextLookup));
        });
    }
}
