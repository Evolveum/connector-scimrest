/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.unit.scim;

import com.evolveum.polygon.conndev.api.AttributePath;
import com.evolveum.polygon.conndev.dev.ConnDevSchema;
import com.evolveum.polygon.conndev.json.OpenApiValueMapping;
import com.evolveum.polygon.scimrest.impl.scim.ScimResourceContext;
import com.evolveum.polygon.scimrest.impl.scim.ScimSchemaTranslator;
import com.evolveum.polygon.scimrest.schema.RestAttributeDefinition;
import com.evolveum.polygon.scimrest.schema.RestObjectClassDefinition;
import com.evolveum.polygon.scimrest.schema.RestSchema;
import com.evolveum.polygon.scimrest.schema.RestSchemaBuilderImpl;
import com.evolveum.polygon.scimrest.schema.ScimAttributeMapping;
import com.unboundid.scim2.common.types.AttributeDefinition;
import com.unboundid.scim2.common.types.ResourceTypeResource;
import com.unboundid.scim2.common.types.SchemaResource;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.EmbeddedObject;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.testng.annotations.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.util.HashMap;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Schema-level behavior of the "flatten single-value complex attributes" SCIM mapping rule:
 * single-valued complex attributes are expanded into plain {@code <parent>_<sub>} attributes
 * with a deep SCIM path instead of an embedded object class; multi-valued complex attributes
 * keep the embedded object mapping.
 */
public class ScimFlattenSingleValueComplexSchemaTest {

    private static final class StubConnector implements Connector {
        @Override public Configuration getConfiguration() { return null; }
        @Override public void init(Configuration c) { }
        @Override public void dispose() { }
    }

    private static RestSchema translate(boolean flatten, ScimResourceContext... resources) {
        var builder = new RestSchemaBuilderImpl(StubConnector.class, null);
        var translator = new ScimSchemaTranslator(null, flatten);
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

    private static ScimResourceContext userResource() {
        var nameAttr = complex("name", List.of(
                scalar("formatted", AttributeDefinition.Type.STRING, false),
                scalar("familyName", AttributeDefinition.Type.STRING, false),
                scalar("givenName", AttributeDefinition.Type.STRING, false)
        ));
        var emailAttr = complexMulti("emails", List.of(
                scalar("value", AttributeDefinition.Type.STRING, false)
        ));
        var schema = new SchemaResource("urn:test:User", "User", "User", List.of(nameAttr, emailAttr));
        var resourceType = new ResourceTypeResource("User", "User", "User resource",
                URI.create("http://localhost/Users"), URI.create("urn:test:User"), List.of());
        return new ScimResourceContext(resourceType, "/Users", schema, new HashMap<>());
    }

    /*----------------------------------------------------------------------*/
    /* Schema structure
    /*----------------------------------------------------------------------*/

    @Test
    public void singleValueComplexAttributeIsFlattened() {
        var restSchema = translate(true, userResource());
        var user = restSchema.objectClass("User");

        var flattened = List.of("name_formatted", "name_familyName", "name_givenName");
        for (var name : flattened) {
            var attr = findAttr(user, name);
            assertEquals(attr.connId().getType(), String.class, name + " should be String");
            assertFalse(attr.connId().isMultiValued(), name + " should be single-valued");
        }

        assertNull(user.attributeFromProtocolName("name"), "the complex attribute itself must not exist");
        assertNull(restSchema.objectClass("User__name"), "no embedded class for a flattened attribute");
    }

    @Test
    public void flattenedAttributesCarryDeepScimPath() {
        var restSchema = translate(true, userResource());
        var user = restSchema.objectClass("User");

        assertEquals(findAttr(user, "name_formatted").scim().path(),
                AttributePath.of("name", "formatted"));
        assertEquals(findAttr(user, "name_familyName").scim().path(),
                AttributePath.of("name", "familyName"));
    }

    @Test
    public void multiValuedComplexAttributeIsNotFlattened() {
        var restSchema = translate(true, userResource());
        var user = restSchema.objectClass("User");

        var emails = findAttr(user, "emails");
        assertEquals(emails.connId().getType(), EmbeddedObject.class, "emails should stay EmbeddedObject");
        assertTrue(emails.connId().isMultiValued(), "emails should be multi-valued");
        assertNotNull(restSchema.objectClass("User__emails"), "embedded class of emails must exist");
    }

    @Test
    public void flatteningDisabledByDefault() {
        var restSchema = translate(false, userResource());
        var user = restSchema.objectClass("User");

        var name = findAttr(user, "name");
        assertEquals(name.connId().getType(), EmbeddedObject.class, "name should be EmbeddedObject by default");
        assertNotNull(restSchema.objectClass("User__name"));
        assertNull(user.attributeFromProtocolName("name_formatted"));
    }

    /*----------------------------------------------------------------------*/
    /* Value mapping: read (flatten) and write (deflatten) round trip
    /*----------------------------------------------------------------------*/

    @Test
    public void flattenedAttributeReadsNestedScimValue() {
        var restSchema = translate(true, userResource());
        var user = restSchema.objectClass("User");
        var mapping = findAttr(user, "name_formatted").scim();

        var objectNode = (ObjectNode) MAPPER.readTree("""
                { "id": "1", "name": { "formatted": "John Doe", "familyName": "Doe" } }
                """);

        assertEquals(mapping.valuesFromObject(objectNode), List.of("John Doe"));
    }

    @Test
    public void flattenedAttributeWritesNestedScimValue() {
        var restSchema = translate(true, userResource());
        var user = restSchema.objectClass("User");
        var formatted = findAttr(user, "name_formatted").scim();
        var familyName = findAttr(user, "name_familyName").scim();

        var objectNode = MAPPER.createObjectNode();
        formatted.toJsonNode(AttributeBuilder.build("name_formatted", "John Doe"), objectNode);
        familyName.toJsonNode(AttributeBuilder.build("name_familyName", "Doe"), objectNode);

        assertEquals(objectNode.at("/name/formatted").asText(), "John Doe");
        assertEquals(objectNode.at("/name/familyName").asText(), "Doe");
        assertFalse(objectNode.has("name_formatted"), "flat keys must not leak into the payload");

        // the round trip: what was written is read back
        assertEquals(formatted.valuesFromObject(objectNode), List.of("John Doe"));
        assertEquals(familyName.valuesFromObject(objectNode), List.of("Doe"));
    }

    @Test
    public void deflattenMergesIntoExistingNestedObject() {
        var restSchema = translate(true, userResource());
        var user = restSchema.objectClass("User");
        var formatted = findAttr(user, "name_formatted").scim();

        // an existing nested object (e.g. partially written by another attribute) is extended, not replaced
        var objectNode = MAPPER.createObjectNode();
        objectNode.set("name", MAPPER.createObjectNode().put("givenName", "John"));
        formatted.toJsonNode(AttributeBuilder.build("name_formatted", "John Doe"), objectNode);

        assertEquals(objectNode.at("/name/givenName").asText(), "John");
        assertEquals(objectNode.at("/name/formatted").asText(), "John Doe");
    }

    @Test
    public void plainSingleComponentPathKeepsDefaultWriteBehavior() {
        var mapping = new ScimAttributeMapping(AttributePath.of("userName"),
                OpenApiValueMapping.from("string", null));

        var objectNode = MAPPER.createObjectNode();
        mapping.toJsonNode(AttributeBuilder.build("userName", "jdoe"), objectNode);

        assertEquals(objectNode.at("/userName").asText(), "jdoe");
    }

    /*----------------------------------------------------------------------*/
    /* Helpers
    /*----------------------------------------------------------------------*/

    private static final JsonMapper MAPPER = new JsonMapper();

    private static AttributeDefinition scalar(String name, AttributeDefinition.Type type,
                                               boolean required) {
        return new AttributeDefinition.Builder()
                .setName(name)
                .setType(type)
                .setRequired(required)
                .setMutability(AttributeDefinition.Mutability.READ_WRITE)
                .build();
    }

    private static AttributeDefinition complex(String name, List<AttributeDefinition> subAttrs) {
        return new AttributeDefinition.Builder()
                .setName(name)
                .setType(AttributeDefinition.Type.COMPLEX)
                .addSubAttributes(subAttrs.toArray(AttributeDefinition[]::new))
                .setMutability(AttributeDefinition.Mutability.READ_WRITE)
                .build();
    }

    private static AttributeDefinition complexMulti(String name, List<AttributeDefinition> subAttrs) {
        return new AttributeDefinition.Builder()
                .setName(name)
                .setType(AttributeDefinition.Type.COMPLEX)
                .addSubAttributes(subAttrs.toArray(AttributeDefinition[]::new))
                .setMultiValued(true)
                .setMutability(AttributeDefinition.Mutability.READ_WRITE)
                .build();
    }

    private static RestAttributeDefinition findAttr(RestObjectClassDefinition oc, String name) {
        var attr = oc.attributeFromProtocolName(name);
        assertNotNull(attr, "Attribute not found: " + name);
        return attr;
    }
}
