/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.schema.strategy;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.scimrest.groovy.api.RestAttributeBuilder;
import com.evolveum.polygon.scimrest.groovy.api.RestObjectClassSchemaBuilder;
import com.evolveum.polygon.scimrest.groovy.api.RestReferenceAttributeBuilder;
import com.evolveum.polygon.scimrest.impl.scim.ScimCoreSchemaUrns;
import com.evolveum.polygon.scimrest.impl.scim.ScimMemberToConnectorObjectReference;
import com.evolveum.polygon.scimrest.impl.scim.ScimGroupToConnectorObjectReference;
import com.evolveum.polygon.scimrest.impl.scim.ScimResourceContext;
import com.evolveum.polygon.scimrest.schema.ScimAttributeMappingRule;
import com.evolveum.polygon.scimrest.schema.ScimMappingAction;
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
    public boolean checkIfApplicable(ScimAttributeMappingRule.Context context, RestObjectClassSchemaBuilder objectClass, RestAttributeBuilder<RestReferenceAttributeBuilder> attribute) {
        var resource = context.resource();
        var attrDef = context.attribute();
        if (attrDef == null || resource.primarySchema() == null) {
            return false;
        }
        String schemaId = resource.primarySchema().getId();
        String attrName = attrDef.getName();
        return isMembershipReference(schemaId, attrName);
    }

    /** Whether the given SCIM schema/attribute name pair is a standard membership reference. */
    public static boolean isMembershipReference(String schemaId, String attrName) {
        return (ScimCoreSchemaUrns.USER_SCHEMA_URN.equals(schemaId) && USER_GROUPS_ATTR.equals(attrName))
                || (ScimCoreSchemaUrns.GROUP_SCHEMA_URN.equals(schemaId) && GROUP_MEMBERS_ATTR.equals(attrName));
    }

    @Override
    public ScimMappingAction createAction(ScimAttributeMappingRule.Context context) {
        String schemaId = context.resource().primarySchema().getId();
        String attrName = context.attribute().getName();
        boolean isGroupMembership = ScimCoreSchemaUrns.USER_SCHEMA_URN.equals(schemaId) && USER_GROUPS_ATTR.equals(attrName);

        return new ScimMappingAction() {
            @Override
            public void applyToAttribute(RestAttributeBuilder<RestReferenceAttributeBuilder> attribute) {
                if (isGroupMembership) {
                    applyGroupMembership(attribute);
                } else {
                    applyUserGroupMembership(attribute);
                }
            }
        };
    }

    private void applyGroupMembership(RestAttributeBuilder<RestReferenceAttributeBuilder> attribute) {
        var groupOc = resourceToObjectClass.get("Group");
        attribute.connId().type(ConnectorObjectReference.class);
        var reference = (RestReferenceAttributeBuilder) attribute;
        if (groupOc != null) {
            reference.objectClass(groupOc);
        }
        reference.subtype("_User_Group_Membership");
        reference.role(AttributeInfo.RoleInReference.SUBJECT);
        attribute.scim().implementation(new ScimGroupToConnectorObjectReference(new ObjectClass(groupOc)));
    }

    private void applyUserGroupMembership(RestAttributeBuilder<RestReferenceAttributeBuilder> attribute) {
        var userOc = resourceToObjectClass.get("User");
        attribute.connId().type(ConnectorObjectReference.class);
        var reference = (RestReferenceAttributeBuilder) attribute;
        if (userOc != null) {
            reference.objectClass(userOc);
        }
        reference.role(AttributeInfo.RoleInReference.OBJECT);
        reference.subtype("_User_Group_Membership");
        attribute.scim().implementation(new ScimMemberToConnectorObjectReference(contextLookup));
    }
}
