/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.unit.scim;

import com.evolveum.polygon.conndev.dev.ConnDevObjectClassSerializer;
import com.evolveum.polygon.conndev.dev.ConnDevSchema;
import com.evolveum.polygon.scimrest.impl.scim.ScimResourceContext;
import com.evolveum.polygon.scimrest.impl.scim.ScimSchemaTranslator;
import com.evolveum.polygon.scimrest.schema.RestSchema;
import com.evolveum.polygon.scimrest.schema.RestSchemaBuilder;
import com.unboundid.scim2.common.types.AttributeDefinition;
import com.unboundid.scim2.common.types.ResourceTypeResource;
import com.unboundid.scim2.common.types.SchemaResource;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.EmbeddedObject;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Development-mode export derived from the one framework schema model: SCIM discovery is translated
 * into {@link RestSchema} by {@link ScimSchemaTranslator} and the {@code conndev_ObjectClass} objects
 * are serialized from that model by the shared {@link ConnDevObjectClassSerializer} — the same model
 * that produces the ConnId schema. Deterministic, no network.
 */
public class ScimDevSchemaExportTest {

    private static final String USER_URN = "urn:ietf:params:scim:schemas:core:2.0:User";
    private static final String GROUP_URN = "urn:ietf:params:scim:schemas:core:2.0:Group";

    private static final class StubConnector implements Connector {
        @Override public Configuration getConfiguration() { return null; }
        @Override public void init(Configuration c) { }
        @Override public void dispose() { }
    }

    @Test
    public void derivesExportFromTranslatedSchemaModel() {
        var userName = new AttributeDefinition.Builder()
                .setName("userName").setType(AttributeDefinition.Type.STRING)
                .setRequired(true).setMutability(AttributeDefinition.Mutability.READ_WRITE).build();
        var displayName = new AttributeDefinition.Builder()
                .setName("displayName").setType(AttributeDefinition.Type.STRING)
                .setMutability(AttributeDefinition.Mutability.READ_ONLY).build();
        var lastModified = new AttributeDefinition.Builder()
                .setName("lastModified").setType(AttributeDefinition.Type.DATETIME)
                .setMutability(AttributeDefinition.Mutability.READ_WRITE).build();
        var schema = new SchemaResource(USER_URN, "User", "User schema",
                List.of(userName, displayName, lastModified));

        var objects = export(resource("User", "/Users", USER_URN, schema));

        assertEquals(objects.size(), 1);
        var user = objects.get(0);
        assertEquals(user.getObjectClass().getObjectClassValue(), "conndev_ObjectClass");
        assertEquals(user.getName().getNameValue(), "User");
        assertEquals(value(user, "locator"), "/Users");
        assertEquals(value(user, "namespace"), USER_URN);

        // the model maps "id" to __UID__, but the export keeps the original framework view
        var attributes = user.getAttributeByName("attributes").getValue();
        var names = attributes.stream()
                .map(e -> string((EmbeddedObject) e, "name")).collect(Collectors.toSet());
        assertEquals(names, Set.of("id", "userName", "displayName", "lastModified"));

        var userNameAttr = embedded(attributes, "userName");
        assertEquals(string(userNameAttr, "type"), "string");
        assertEquals(single(userNameAttr, "required"), Boolean.TRUE);

        // mutability came through the model's ConnId flags (sparse emission: only deviations)
        var displayNameAttr = embedded(attributes, "displayName");
        assertEquals(single(displayNameAttr, "creatable"), Boolean.FALSE);
        assertEquals(single(displayNameAttr, "updateable"), Boolean.FALSE);
        assertNull(AttributeUtil.find("creatable", userNameAttr.getAttributes()));

        // the native SCIM type survives even though ConnId maps dateTime to a plain string
        assertEquals(string(embedded(attributes, "lastModified"), "type"), "dateTime");
    }

    @Test
    public void exportsMembershipReferenceFromTheModel() {
        // 'userName'/'displayName' drive the __NAME__ mapping; 'members' is the Group-side membership.
        var userName = new AttributeDefinition.Builder()
                .setName("userName").setType(AttributeDefinition.Type.STRING)
                .setMutability(AttributeDefinition.Mutability.READ_WRITE).build();
        var userSchema = new SchemaResource(USER_URN, "User", "User schema", List.of(userName));

        var displayName = new AttributeDefinition.Builder()
                .setName("displayName").setType(AttributeDefinition.Type.STRING)
                .setMutability(AttributeDefinition.Mutability.READ_WRITE).build();
        var members = new AttributeDefinition.Builder()
                .setName("members").setType(AttributeDefinition.Type.STRING).setMultiValued(true)
                .setMutability(AttributeDefinition.Mutability.READ_WRITE).build();
        var groupSchema = new SchemaResource(GROUP_URN, "Group", "Group schema",
                List.of(displayName, members));

        var objects = export(
                resource("User", "/Users", USER_URN, userSchema),
                resource("Group", "/Groups", GROUP_URN, groupSchema));

        var group = objects.stream()
                .filter(o -> "Group".equals(o.getName().getNameValue()))
                .findFirst().orElseThrow();
        var membersAttr = embedded(group.getAttributeByName("attributes").getValue(), "members");
        // the reference is expressed by referencedObjectClass/reference/role; type keeps the native view
        assertEquals(string(membersAttr, "referencedObjectClass"), "User");
        assertEquals(string(membersAttr, "reference"), "_User_Group_Membership");
        assertEquals(string(membersAttr, "role"), "object");
        assertEquals(single(membersAttr, "multiValued"), Boolean.TRUE);
    }

    @Test
    public void devObjectClassesAreDeclaredButNotExported() {
        var userName = new AttributeDefinition.Builder()
                .setName("userName").setType(AttributeDefinition.Type.STRING)
                .setMutability(AttributeDefinition.Mutability.READ_WRITE).build();
        var schema = new SchemaResource(USER_URN, "User", "User schema", List.of(userName));

        var restSchema = translate(resource("User", "/Users", USER_URN, schema));

        // declared in the ConnId schema (so midPoint can search them) ...
        var declared = restSchema.connIdSchema().getObjectClassInfo().stream()
                .map(info -> info.getType()).collect(Collectors.toSet());
        assertTrue(declared.contains("conndev_ObjectClass"));
        assertTrue(declared.contains("conndev_Attribute"));

        // ... but the export contains only the application object classes
        var exported = ConnDevObjectClassSerializer.serializeAll(restSchema.objectClasses()).stream()
                .map(o -> o.getName().getNameValue()).collect(Collectors.toSet());
        assertEquals(exported, Set.of("User"));
    }

    // --- The production translation path: correlate + populate + build, then serialize ---

    private static ScimResourceContext resource(String name, String endpoint, String urn, SchemaResource schema) {
        var resourceType = new ResourceTypeResource(name, name, name + " resource",
                URI.create(endpoint), URI.create(urn), List.of());
        return new ScimResourceContext(resourceType, endpoint, schema, new HashMap<>());
    }

    private static RestSchema translate(ScimResourceContext... resources) {
        var builder = new RestSchemaBuilder(StubConnector.class, null);
        var translator = new ScimSchemaTranslator(null);
        for (var resource : resources) {
            translator.correlateObjectClasses(resource, builder);
        }
        for (var resource : resources) {
            translator.populateSchema(resource, builder);
        }
        for (var info : ConnDevSchema.objectClassInfos()) {
            builder.defineObjectClass(info);
        }
        return builder.build();
    }

    private static List<ConnectorObject> export(ScimResourceContext... resources) {
        return ConnDevObjectClassSerializer.serializeAll(translate(resources).objectClasses());
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
