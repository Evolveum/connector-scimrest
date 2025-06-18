package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.ContextLookup;
import com.evolveum.polygon.scim.rest.ScimResourceContext;
import com.evolveum.polygon.scim.rest.schema.MappedAttributeBuilderImpl;
import com.evolveum.polygon.scim.rest.schema.MappedObjectClassBuilder;
import com.evolveum.polygon.scim.rest.schema.RestSchemaBuilder;
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
                attribute.scim().type(scimAttr.getType().getName());
                attribute.description(scimAttr.getDescription());
                attribute.multiValued(scimAttr.isMultiValued());
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
