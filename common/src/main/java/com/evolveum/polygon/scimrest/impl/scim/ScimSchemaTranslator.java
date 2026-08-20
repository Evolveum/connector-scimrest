/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.scim;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.json.OpenApiValueMapping;
import com.evolveum.polygon.scimrest.schema.RestAttributeBuilderImpl;
import com.evolveum.polygon.scimrest.schema.MappedObjectClassBuilder;
import com.evolveum.polygon.scimrest.schema.RestSchemaBuilderImpl;
import com.unboundid.scim2.common.types.AttributeDefinition;
import com.unboundid.scim2.common.types.ResourceTypeResource;
import com.unboundid.scim2.common.types.SchemaResource;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.ConnectorObjectReference;
import org.identityconnectors.framework.common.objects.EmbeddedObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.evolveum.polygon.conndev.concepts.DefinitionValue.detected;
import static com.evolveum.polygon.conndev.concepts.DefinitionValue.emptyDefault;

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

    public void correlateObjectClasses(ScimResourceContext scim, RestSchemaBuilderImpl schema) {

        // First we try to match object class by SCIM URN
        var objectClass = findOrCreateObjectClass(scim.resource(), schema);

        resourceToObjectClass.put(scim.resource().getName(), objectClass.name());
        objectClassToResource.put(objectClass.name(), scim);

        if (objectClass.description() == null) {
            objectClass.description(scim.resource().getDescription());
        }
        // We map the object class to this SCIM resource
        objectClass.scim().name(scim.resource().getName());
        if (scim.schemaUri() != null) {
            objectClass.scim().schemaUri(scim.schemaUri().toString());
        }
    }

    public void populateSchema(ScimResourceContext scim, RestSchemaBuilderImpl schema) {
        var objectClassName = resourceToObjectClass.get(scim.resource().getName());
        var objectClass = schema.objectClass(objectClassName);
        populateBuiltInSchema(objectClass, objectClass.scim().isOnlyExplicitlyListed());
        populatePrimarySchema(scim.primarySchema(), schema, objectClass, objectClassName,
                              objectClass.scim().isOnlyExplicitlyListed());

        populatePathBasedSchema(scim, objectClass);
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

    private void populateAttribute(RestAttributeBuilderImpl attribute, AttributeDefinition scimAttr) {

        switch (scimAttr.getType()) {
            case DATETIME -> {
                attribute.scim().type("string");
                attribute.scim().implementation(OpenApiValueMapping.DateTime);
            }
            case DECIMAL -> {
                attribute.scim().type("number");
                attribute.scim().implementation(OpenApiValueMapping.Decimal);
            }
            case REFERENCE -> attribute.scim().type("string");
            default -> attribute.scim().type(jsonType(scimAttr.getType()));
        }




        populateAttributeMetadata(attribute, scimAttr);
    }
    private void populateAttributeMetadata(RestAttributeBuilderImpl attribute, AttributeDefinition scimAttr) {
        attribute.nativeType(scimAttr.getType().getName());
        attribute.connId().description(detected(scimAttr.getDescription()));
        attribute.connId().required(detected(scimAttr.isRequired()));
        attribute.connId().multiValued(detected(scimAttr.isMultiValued()));
        attribute.connId().returnedByDefault(detected(AttributeDefinition.Returned.DEFAULT.equals(scimAttr.getReturned())));
        switch (scimAttr.getMutability()) {
            case IMMUTABLE -> {
                attribute.connId().readable(detected(true)).creatable(detected(true)).updatable(detected(false));
            }
            case READ_ONLY -> {
                attribute.connId().readable(detected(true)).creatable(detected(false)).updatable(detected(false));
            }
            case READ_WRITE -> {
                attribute.connId().readable(detected(true)).creatable(detected(true)).updatable(detected(true));
            }
            case WRITE_ONLY -> {
                attribute.connId().readable(detected(false)).creatable(detected(true)).updatable(detected(true));
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

    private void populatePrimarySchema(SchemaResource schemaResource,
                                       RestSchemaBuilderImpl schema,
                                       MappedObjectClassBuilder objectClass,
                                       String objectClassName,
                                       boolean onlyListed) {
        for (var scimAttr : schemaResource.getAttributes()) {
            if (AttributeDefinition.Type.COMPLEX.equals(scimAttr.getType())) {
                if (!isMembershipReference(schemaResource.getId(), scimAttr.getName())) {
                    populateComplexAttribute(scimAttr, schema, objectClass, objectClassName, onlyListed);
                    continue;
                }
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
                        handleUserGroupsAttribute(attribute, objectClass);
                    }
                }
            } else if (GROUP_SCHEMA_URN.equals(schemaResource.getId())) {
                switch (scimAttr.getName()) {
                    case "displayName" -> {
                        attribute.connId().name(Name.NAME);
                    }
                    case "members" -> {
                        handleGroupMembersAttribute(attribute, objectClass);
                    }
                }
            }

        }
    }

    private boolean isMembershipReference(String schemaId, String attrName) {
        return (USER_SCHEMA_URN.equals(schemaId) && "groups".equals(attrName)) ||
               (GROUP_SCHEMA_URN.equals(schemaId) && "members".equals(attrName));
    }

    private void handleUserGroupsAttribute(RestAttributeBuilderImpl attribute, MappedObjectClassBuilder objectClass) {
        var groupOc = resourceToObjectClass.get("Group");
        if (attribute != null) {
            attribute.connId().type(ConnectorObjectReference.class);
        }
        if (groupOc != null) {
            attribute.objectClass(groupOc);
        }
        attribute.subtype("_User_Group_Membership");
        attribute.role(AttributeInfo.RoleInReference.SUBJECT);
        attribute.scim().implementation(new ScimGroupToConnectorObjectReference(new ObjectClass(groupOc)));
    }

    private void handleGroupMembersAttribute(RestAttributeBuilderImpl attribute, MappedObjectClassBuilder objectClass) {
        var userOc = resourceToObjectClass.get("User");
        if (attribute != null) {
            attribute.connId().type(ConnectorObjectReference.class);
        }
        if (userOc != null) {
            attribute.objectClass(userOc);
        }
        attribute.role(AttributeInfo.RoleInReference.OBJECT);
        attribute.subtype("_User_Group_Membership");
        attribute.scim().implementation(new ScimMemberToConnectorObjectReference(contextLookup));
    }

    private boolean isUserGroupsAttribute(String id, String name) {
        return USER_SCHEMA_URN.equals(id) && USER_GROUPS_ATTR_NAME.equals(name);
    }

    private void populateComplexAttribute(AttributeDefinition scimAttr,
                                          RestSchemaBuilderImpl schema,
                                          MappedObjectClassBuilder parentOc,
                                                String parentOcName,
                                          boolean onlyListed) {
        // Only process if we're not in "only explicitly listed" mode
        if (onlyListed) {
            return;
        }

        if (!parentOc.findAttributes(p -> scimAttr.getName().equals(p.scim().name())).isEmpty()) {
            return;
        }
        if (!parentOc.findAttributes(c -> scimAttr.getName().equals(c.name())).isEmpty()) {
            // Attribute already existed (was defined by groovy script), skip
            return;
        }
        String attrName = scimAttr.getName();
        var complexAttr = parentOc.attribute(attrName);

        String embeddedClassName = parentOcName + "__" + scimAttr.getName();
        // FIXME: This should be lookuped up using scim / path
        var embeddedBuilder = schema.objectClass(embeddedClassName);
        embeddedBuilder.embedded(true);

        for (AttributeDefinition subAttr : scimAttr.getSubAttributes()) {
            if (AttributeDefinition.Type.COMPLEX.equals(subAttr.getType())) {
                continue;
            }
            var attribute = findOrCreateAttribute(subAttr, embeddedBuilder, onlyListed);
            if (attribute != null) {
                attribute.scim().name(subAttr.getName());
                populateAttribute(attribute, subAttr);
            }
        }

        complexAttr.scim()
                .name(scimAttr.getName())
                .implementation(new ScimEmbeddedObjectValueMapping(contextLookup, embeddedClassName));
        complexAttr.connId()
                .type(EmbeddedObject.class)
                .referencedObjectClassName(detected(embeddedClassName))
                .multiValued(detected(scimAttr.isMultiValued()))
                .required(detected(scimAttr.isRequired()))
                .roleInReference(detected(AttributeInfo.RoleInReference.SUBJECT.toString()))
                .returnedByDefault(detected(
                        AttributeDefinition.Returned.DEFAULT.equals(scimAttr.getReturned())));
    }

    /**
     * Maps a SCIM attribute type to the JSON value type the value mappings understand. SCIM-only
     * types (dateTime, reference, decimal) have no direct JSON mapping — the values travel as their
     * JSON representation; the original SCIM type is kept as the attribute's native type.
     */
    private static String jsonType(AttributeDefinition.Type type) {
        return switch (type) {
            case DATETIME, REFERENCE -> "string";
            case DECIMAL -> "number";
            default -> type.getName();
        };
    }

    private RestAttributeBuilderImpl findOrCreateAttribute(AttributeDefinition scimAttr, MappedObjectClassBuilder objectClass, boolean onlyListed) {
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

    private MappedObjectClassBuilder findOrCreateObjectClass(ResourceTypeResource scim, RestSchemaBuilderImpl schema) {
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
