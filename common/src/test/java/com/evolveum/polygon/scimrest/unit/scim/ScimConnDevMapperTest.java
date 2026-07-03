/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.unit.scim;

import com.evolveum.polygon.scimrest.impl.scim.dev.ScimConnDevMapper;
import com.unboundid.scim2.common.types.AttributeDefinition;
import com.unboundid.scim2.common.types.ResourceTypeResource;
import com.unboundid.scim2.common.types.SchemaResource;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.EmbeddedObject;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

/**
 * New SCIM dev-mode mapping (separate from {@code ScimSchemaTranslator}): a {@link ResourceTypeResource}
 * (its primary schema plus extensions) maps to a single {@code conndev_ObjectClass} {@link ConnectorObject}.
 * All such objects share the one
 * {@code conndev_ObjectClass} type so midPoint reads them uniformly; {@code __NAME__} says which entity
 * is described. The object carries a multi-valued {@code attributes} (scalar attributes keep their
 * {@code subAttributes}; a reference is an attribute carrying {@code referencedObjectClass}).
 * Deterministic, no network.
 */
public class ScimConnDevMapperTest {

    @Test
    public void mapsSchemaToConndevObjectClassWithReferenceAttributes() {
        var userName = new AttributeDefinition.Builder()
                .setName("userName").setType(AttributeDefinition.Type.STRING)
                .setRequired(true).setMutability(AttributeDefinition.Mutability.READ_WRITE).build();
        var familyName = new AttributeDefinition.Builder()
                .setName("familyName").setType(AttributeDefinition.Type.STRING).build();
        var givenName = new AttributeDefinition.Builder()
                .setName("givenName").setType(AttributeDefinition.Type.STRING).build();
        var name = new AttributeDefinition.Builder()
                .setName("name").setType(AttributeDefinition.Type.COMPLEX)
                .addSubAttributes(familyName, givenName).build();
        var manager = new AttributeDefinition.Builder()
                .setName("manager").setType(AttributeDefinition.Type.REFERENCE)
                .addReferenceTypes("User").build();
        var schema = new SchemaResource("urn:ietf:params:scim:schemas:core:2.0:User", "User", "User schema",
                List.of(userName, name, manager));

        ConnectorObject result = mapSingleSchema(schema);

        // one uniform type; the entity is in the name
        assertEquals(result.getObjectClass().getObjectClassValue(), "conndev_ObjectClass");
        assertEquals(result.getName().getNameValue(), "User");

        // everything is an attribute; a reference is an attribute carrying referencedObjectClass
        var attributes = result.getAttributeByName("attributes").getValue();
        var attributeNames = attributes.stream()
                .map(e -> string((EmbeddedObject) e, "name")).collect(Collectors.toSet());
        assertEquals(attributeNames, Set.of("userName", "name", "manager"));

        var userNameAttr = embedded(attributes, "userName");
        assertEquals(string(userNameAttr, "type"), "string");
        assertEquals(single(userNameAttr, "required"), Boolean.TRUE);

        var nameAttr = embedded(attributes, "name");
        assertEquals(string(nameAttr, "type"), "complex");
        var subAttributes = AttributeUtil.find("subAttributes", nameAttr.getAttributes()).getValue();
        var subNames = subAttributes.stream()
                .map(e -> string((EmbeddedObject) e, "name")).collect(Collectors.toSet());
        assertEquals(subNames, Set.of("familyName", "givenName"), "complex attribute keeps its sub-attributes");

        // the reference (manager -> User) is expressed on the attribute itself
        var managerAttr = embedded(attributes, "manager");
        assertEquals(string(managerAttr, "referencedObjectClass"), "User");
    }

    @Test
    public void mapsMembershipComplexWithRefAsReferenceAttribute() {
        // SCIM membership: a COMPLEX multi-valued attribute with a $ref sub-attribute (e.g. User.groups).
        var value = new AttributeDefinition.Builder()
                .setName("value").setType(AttributeDefinition.Type.STRING).build();
        var ref = new AttributeDefinition.Builder()
                .setName("$ref").setType(AttributeDefinition.Type.REFERENCE).addReferenceTypes("Group").build();
        var groups = new AttributeDefinition.Builder()
                .setName("groups").setType(AttributeDefinition.Type.COMPLEX).setMultiValued(true)
                .addSubAttributes(value, ref).build();
        var schema = new SchemaResource("urn:ietf:params:scim:schemas:core:2.0:User", "User", "User schema",
                List.of(groups));

        ConnectorObject result = mapSingleSchema(schema);

        // groups is an attribute carrying the reference (target from the $ref sub-attribute)
        var attributes = result.getAttributeByName("attributes").getValue();
        var groupsAttr = embedded(attributes, "groups");
        assertEquals(string(groupsAttr, "referencedObjectClass"), "Group", "target from the $ref sub-attribute");
    }

    @Test
    public void mapsMembershipByCoreConventionWhenServerOmitsRef() {
        // Real servers (e.g. Keycloak) publish 'groups' as a bare complex attribute — no $ref, no
        // subAttributes, no referenceTypes. The target/role then comes from the SCIM-core convention.
        var groups = new AttributeDefinition.Builder()
                .setName("groups").setType(AttributeDefinition.Type.COMPLEX).setMultiValued(true).build();
        var schema = new SchemaResource("urn:ietf:params:scim:schemas:core:2.0:User", "User", "User schema",
                List.of(groups));

        ConnectorObject result = mapSingleSchema(schema);

        // the bare 'groups' complex is recognized as membership and carries the reference
        var attributes = result.getAttributeByName("attributes").getValue();
        var groupsAttr = embedded(attributes, "groups");
        assertEquals(string(groupsAttr, "referencedObjectClass"), "Group", "target from SCIM-core convention");
        assertEquals(string(groupsAttr, "role"), "subject");
        assertEquals(single(groupsAttr, "multiValued"), Boolean.TRUE);
    }

    @Test
    public void linksResourceTypeWithPrimaryAndExtensionIntoOneObjectClass() {
        var coreUrn = "urn:ietf:params:scim:schemas:core:2.0:User";
        var enterpriseUrn = "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User";

        var userName = new AttributeDefinition.Builder()
                .setName("userName").setType(AttributeDefinition.Type.STRING).setRequired(true).build();
        var coreSchema = new SchemaResource(coreUrn, "User", "User", List.of(userName));

        var employeeNumber = new AttributeDefinition.Builder()
                .setName("employeeNumber").setType(AttributeDefinition.Type.STRING).build();
        var enterpriseSchema = new SchemaResource(enterpriseUrn, "EnterpriseUser", "Enterprise User",
                List.of(employeeNumber));

        var resourceType = new ResourceTypeResource("User", "User", "User Account",
                URI.create("/Users"), URI.create(coreUrn),
                List.of(new ResourceTypeResource.SchemaExtension(URI.create(enterpriseUrn), true)));

        ConnectorObject result = new ScimConnDevMapper()
                .toObjectClass(resourceType, Map.of(coreUrn, coreSchema, enterpriseUrn, enterpriseSchema));

        // one object class: User, carrying its endpoint + primary-schema namespace
        assertEquals(result.getName().getNameValue(), "User");
        assertEquals(value(result, "endpoint"), "/Users");
        assertEquals(value(result, "namespace"), coreUrn);

        // attributes merged: core (no namespace) + extension (tagged with the extension URN)
        var attributes = result.getAttributeByName("attributes").getValue();
        var names = attributes.stream()
                .map(e -> string((EmbeddedObject) e, "name")).collect(Collectors.toSet());
        assertEquals(names, Set.of("userName", "employeeNumber"));

        assertEquals(string(embedded(attributes, "userName"), "namespace"), null,
                "primary attributes inherit the object-class namespace");
        assertEquals(string(embedded(attributes, "employeeNumber"), "namespace"), enterpriseUrn,
                "extension attributes are tagged with their source schema URN");
    }

    /**
     * Maps a single schema through the production resource-type path (wrapping it in a minimal
     * {@link ResourceTypeResource}), so these tests exercise the same code the connector runs.
     */
    private static ConnectorObject mapSingleSchema(SchemaResource schema) {
        var urn = schema.getId();
        var resourceType = new ResourceTypeResource(schema.getName(), schema.getName(), schema.getName(),
                URI.create("/" + schema.getName() + "s"), URI.create(urn), List.of());
        return new ScimConnDevMapper().toObjectClass(resourceType, Map.of(urn, schema));
    }

    private static String value(ConnectorObject object, String name) {
        var attribute = object.getAttributeByName(name);
        return attribute == null ? null : AttributeUtil.getStringValue(attribute);
    }

    private static EmbeddedObject embedded(List<Object> values, String name) {
        return values.stream().map(EmbeddedObject.class::cast)
                .filter(e -> name.equals(string(e, "name")))
                .findFirst().orElseThrow(() -> new AssertionError("No embedded attribute named " + name));
    }

    private static String string(EmbeddedObject object, String name) {
        var attribute = AttributeUtil.find(name, object.getAttributes());
        return attribute == null ? null : AttributeUtil.getStringValue(attribute);
    }

    private static Object single(EmbeddedObject object, String name) {
        Attribute attribute = AttributeUtil.find(name, object.getAttributes());
        assertNotNull(attribute, "expected " + name);
        return AttributeUtil.getSingleValue(attribute);
    }
}
