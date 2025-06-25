/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.scim;

import com.evolveum.polygon.scimrest.ContextLookup;
import com.evolveum.polygon.scimrest.schema.MappedAttributeBuilderImpl;
import com.evolveum.polygon.scimrest.schema.MappedObjectClassBuilder;
import com.evolveum.polygon.scimrest.schema.RestSchemaBuilder;
import com.unboundid.scim2.common.types.AttributeDefinition;
import com.unboundid.scim2.common.types.ResourceTypeResource;
import com.unboundid.scim2.common.types.SchemaResource;
import org.identityconnectors.framework.common.objects.*;

import java.util.HashMap;
import java.util.Map;

public class ScimSchemaTranslator {

    private static final String USER_SCHEMA_URN = "urn:ietf:params:scim:schemas:core:2.0:User" ;
    private static final String GROUP_SCHEMA_URN = "urn:ietf:params:scim:schemas:core:2.0:Group" ;

    private static final String USER_GROUPS_ATTR_NAME = "groups" ;

    private Map<String, String> resourceToObjectClass = new HashMap<>();
    private Map<String, ScimResourceContext> objectClassToResource = new HashMap<>();


    private static final AttributeDefinition ID_ATTR = new AttributeDefinition.Builder()
            .setName("id")
            .build();

    private final ContextLookup contextLookup;

    public ScimSchemaTranslator(ContextLookup contextLookup) {
        this.contextLookup = contextLookup;
    }

    public void correlateObjectClasses(ScimResourceContext scim, RestSchemaBuilder schema) {

        // First we try to match object class by SCIM URN
        var objectClass = findOrCreateObjectClass(scim.resource(), schema);

        resourceToObjectClass.put(scim.resource().getName(), objectClass.name());
        objectClassToResource.put(objectClass.name(), scim);

        if (objectClass.description() == null) {
            objectClass.description(scim.resource().getDescription());
        }
        // We map the object class to this SCIM resource
        objectClass.scim().name(scim.resource().getName());


    }

    public void populateSchema(ScimResourceContext scim, RestSchemaBuilder schema) {
        var objectClassName = resourceToObjectClass.get(scim.resource().getName());
        var objectClass = schema.objectClass(objectClassName);
        populateBuiltInSchema(objectClass, objectClass.scim().isOnlyExplicitlyListed());
        populatePrimarySchema(scim.primarySchema(), objectClass, objectClass.scim().isOnlyExplicitlyListed());

        populatePathBasedSchema(scim,objectClass);
        // FIXME populate deep schema
        // eg. resolving things like familyName = name.familyName
        // practically values remapped from deep containers

    }

    private void populatePathBasedSchema(ScimResourceContext scim, MappedObjectClassBuilder objectClass) {
        for (var attr : objectClass.allAttributes()) {
            var path = attr.scim().path();
            if (path == null || path.onlyAttribute() != null) {
                // Path is simple only one attribute, so no it was handled in previous step
                continue;
            }
            var attrDef = scim.findAttributeDefinition(path);
            if (attrDef == null) {
                throw new IllegalStateException(String.format("Attribute '%s' not found", path));
            }
            populateAttribute(attr, attrDef);
        }


    }

    private void populateAttribute(MappedAttributeBuilderImpl attribute, AttributeDefinition scimAttr) {
        attribute.scim().type(scimAttr.getType().getName());
        attribute.description(scimAttr.getDescription());
        attribute.multiValued(scimAttr.isMultiValued());
        attribute.returnedByDefault(AttributeDefinition.Returned.DEFAULT.equals(scimAttr.getReturned()));
        switch (scimAttr.getMutability()) {
            case IMMUTABLE -> {
                attribute.readable(true).creatable(true).updateable(false);
            }
            case READ_ONLY -> {
                attribute.readable(true).creatable(false).updateable(false);
            }
            case READ_WRITE -> {
                attribute.readable(true).creatable(true).updateable(true);
            }
            case WRITE_ONLY -> {
                attribute.readable(false).creatable(true).updateable(true);
            }
        }
    }

    private void populateBuiltInSchema(MappedObjectClassBuilder objectClass, boolean isOnlyExplicitlyListed) {
        var idAttribute = findOrCreateAttribute(ID_ATTR, objectClass, isOnlyExplicitlyListed);
        if (idAttribute != null) {
            idAttribute.scim()
                    .name(ID_ATTR.getName())
                    .type("string");
            // We should check if other attributes
            if (objectClass.connIdAttributeNotDefined(Uid.NAME)) {
                idAttribute.connId().name(Uid.NAME);
            }
        }
    }

    private void populatePrimarySchema(SchemaResource schemaResource, MappedObjectClassBuilder objectClass, boolean onlyListed) {
        for (var scimAttr : schemaResource.getAttributes()) {
            // We should determine if attribute is excluded by name or being structured
            if (AttributeDefinition.Type.COMPLEX.equals(scimAttr.getType())) {
                // skip for now
                continue;
            }

            // Lookup as SCIM attribute first
            var attribute = findOrCreateAttribute(scimAttr, objectClass, onlyListed);
            if (attribute != null) {
                attribute.scim().name(scimAttr.getName());
                populateAttribute(attribute, scimAttr);
            }
            if (USER_SCHEMA_URN.equals(schemaResource.getId())) {
                switch (scimAttr.getName()) {
                    case "userName" -> {
                        if (objectClass.connIdAttributeNotDefined(Name.NAME)) {
                            attribute.connId().name(Name.NAME);
                        }
                    }
                    case "groups" -> {
                        var groupOc = resourceToObjectClass.get("Group");
                        attribute.connId().type(ConnectorObjectReference.class);
                        attribute.subtype("_User_Group_Membership");
                        attribute.role(AttributeInfo.RoleInReference.SUBJECT);
                        attribute.scim().implementation(new ScimGroupToConnectorObjectReference(new ObjectClass(groupOc)));
                    }
                }
            } else if (GROUP_SCHEMA_URN.equals(schemaResource.getId())) {
                switch (scimAttr.getName()) {
                    case "displayName" -> {
                        attribute.connId().name(Name.NAME);
                    }
                    case "members" -> {
                        attribute.connId().type(ConnectorObjectReference.class);
                        attribute.role(AttributeInfo.RoleInReference.OBJECT);
                        attribute.subtype("_User_Group_Membership");
                        attribute.scim().implementation(new ScimMemberToConnectorObjectReference(contextLookup));

                    }
                }
            }

        }
    }

    private boolean isUserGroupsAttribute(String id, String name) {
        return USER_SCHEMA_URN.equals(id) && USER_GROUPS_ATTR_NAME.equals(name);
    }

    private MappedAttributeBuilderImpl findOrCreateAttribute(AttributeDefinition scimAttr, MappedObjectClassBuilder objectClass, boolean onlyListed) {
        for (var attr : objectClass.allAttributes()) {
            if (scimAttr.getName().equals(attr.scim().name())) {
                return attr;
            }
        }
        if (onlyListed) {
            return null;
        }
        // We create new attribute (or use attribute with same name
        return objectClass.attribute(scimAttr.getName());

    }

    private MappedObjectClassBuilder findOrCreateObjectClass(ResourceTypeResource scim, RestSchemaBuilder schema) {
        for (var objClass : schema.allObjectClasses()) {
            if (objClass.embedded()) {

            }
            // We Try to match existing builder by name of SCIM resources
            // this allows for renames based on schema
            if (scim.getName().equals(objClass.scim().name()) ) {
                return objClass;
            }
        }

        for (var objClass : schema.allObjectClasses()) {
            // We Try to match existing builder by uri of scim schema
            // this allows for renames based on schema
            if (scim.getSchema().toString().equals(objClass.scim().schemaUri()) ) {
                return objClass;
            }
        }

        //
        return schema.objectClass(scim.getName());

    }


    public Map<String, String> resourceToObjectClass() {
        return resourceToObjectClass;
    }

    public Map<String, ScimResourceContext> objectClassToResource() {
        return objectClassToResource;
    }
}
