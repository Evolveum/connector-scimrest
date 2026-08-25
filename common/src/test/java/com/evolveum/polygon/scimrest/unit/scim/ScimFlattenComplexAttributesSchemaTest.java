/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.unit.scim;

import com.evolveum.polygon.conndev.api.AttributePath;
import com.evolveum.polygon.conndev.api.AttributePathDeclaration;
import com.evolveum.polygon.conndev.api.JavaPathFormat;
import com.evolveum.polygon.conndev.dev.ConnDevSchema;
import com.evolveum.polygon.conndev.json.OpenApiValueMapping;
import com.evolveum.polygon.scimrest.config.ScimClientConfiguration;
import com.evolveum.polygon.scimrest.impl.scim.ScimResourceContext;
import com.evolveum.polygon.scimrest.impl.scim.ScimSchemaTranslator;
import com.evolveum.polygon.scimrest.impl.scim.flatten.ScimFlattenStrategies;
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
 * Schema-level behavior of the per-family {@code SCIM Mapping} flatten rules: the single-valued
 * {@code name} attribute and the multi-valued {@code emails}/{@code phoneNumbers}/{@code addresses}
 * attributes are each independently expandable into plain attributes (the latter keyed by entry
 * {@code type}); a complex attribute whose family is not enabled keeps its embedded object mapping.
 */
public class ScimFlattenComplexAttributesSchemaTest {

    private static final class StubConnector implements Connector {
        @Override public Configuration getConfiguration() { return null; }
        @Override public void init(Configuration c) { }
        @Override public void dispose() { }
    }

    /** Minimal configuration exposing the four independent flatten flags. */
    private static final class TestConfig implements ScimClientConfiguration {
        private final boolean name, emails, phones, addresses;

        TestConfig(boolean name, boolean emails, boolean phones, boolean addresses) {
            this.name = name;
            this.emails = emails;
            this.phones = phones;
            this.addresses = addresses;
        }

        @Override public String getScimBaseUrl() { return null; }
        @Override public Boolean getScimFlattenNameAttribute() { return name; }
        @Override public Boolean getScimFlattenEmails() { return emails; }
        @Override public Boolean getScimFlattenPhoneNumbers() { return phones; }
        @Override public Boolean getScimFlattenAddresses() { return addresses; }
    }

    private static RestSchema translate(boolean name, boolean emails, boolean phones, boolean addresses,
                                         ScimResourceContext... resources) {
        var builder = new RestSchemaBuilderImpl(StubConnector.class, null);
        var translator = new ScimSchemaTranslator(null,
                ScimFlattenStrategies.forConfiguration(new TestConfig(name, emails, phones, addresses)));
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
                scalar("value", AttributeDefinition.Type.STRING, false),
                scalar("type", AttributeDefinition.Type.STRING, false)
        ));
        var phoneAttr = complexMulti("phoneNumbers", List.of(
                scalar("value", AttributeDefinition.Type.STRING, false),
                scalar("type", AttributeDefinition.Type.STRING, false)
        ));
        var addressAttr = complexMulti("addresses", List.of(
                scalar("type", AttributeDefinition.Type.STRING, false),
                scalar("formatted", AttributeDefinition.Type.STRING, false),
                scalar("locality", AttributeDefinition.Type.STRING, false),
                scalar("region", AttributeDefinition.Type.STRING, false),
                scalar("postalCode", AttributeDefinition.Type.STRING, false),
                scalar("country", AttributeDefinition.Type.STRING, false)
        ));
        var schema = new SchemaResource("urn:test:User", "User", "User",
                List.of(nameAttr, emailAttr, phoneAttr, addressAttr));
        var resourceType = new ResourceTypeResource("User", "User", "User resource",
                URI.create("http://localhost/Users"), URI.create("urn:test:User"), List.of());
        return new ScimResourceContext(resourceType, "/Users", schema, new HashMap<>());
    }

    /*----------------------------------------------------------------------*/
    /* Schema structure
    /*----------------------------------------------------------------------*/

    @Test
    public void singleValueComplexAttributeIsFlattened() {
        var restSchema = translate(true, false, false, false, userResource());
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
    public void multiValuedComplexAttributeIsFlattenedByType() {
        var restSchema = translate(false, true, true, true, userResource());
        var user = restSchema.objectClass("User");

        for (var name : List.of("work_email", "home_email", "other_email",
                "work_phone", "home_phone", "other_phone")) {
            var attr = findAttr(user, name);
            assertEquals(attr.connId().getType(), String.class, name + " should be String");
            assertFalse(attr.connId().isMultiValued(), name + " should be single-valued");
        }

        for (var type : List.of("work", "home", "other")) {
            for (var sub : List.of("formatted", "locality", "region", "postalCode", "country")) {
                assertNotNull(findAttr(user, type + "_address_" + sub), type + "_address_" + sub + " should exist");
            }
        }

        for (var family : List.of("emails", "phoneNumbers", "addresses")) {
            assertNull(user.attributeFromProtocolName(family), family + " complex attribute itself must not exist");
            assertNull(restSchema.objectClass("User__" + family), "no embedded class for a flattened attribute");
        }
    }

    @Test
    public void disabledFamilyKeepsEmbeddedObjectMapping() {
        var restSchema = translate(false, false, false, false, userResource());
        var user = restSchema.objectClass("User");

        for (var family : List.of("name", "emails", "phoneNumbers", "addresses")) {
            var attr = findAttr(user, family);
            assertEquals(attr.connId().getType(), EmbeddedObject.class, family + " should be EmbeddedObject by default");
            assertNotNull(restSchema.objectClass("User__" + family), "embedded class of " + family + " must exist");
        }
        assertNull(user.attributeFromProtocolName("name_formatted"));
        assertNull(user.attributeFromProtocolName("work_email"));
    }

    @Test
    public void onlyEnabledFamilyIsFlattened() {
        var restSchema = translate(true, false, false, false, userResource());
        var user = restSchema.objectClass("User");

        assertNotNull(findAttr(user, "name_formatted"));
        assertNull(user.attributeFromProtocolName("name"), "name flattened");
        assertEquals(findAttr(user, "emails").connId().getType(), EmbeddedObject.class, "emails stays embedded");
        assertEquals(findAttr(user, "addresses").connId().getType(), EmbeddedObject.class, "addresses stays embedded");
    }

    /*----------------------------------------------------------------------*/
    /* SCIM paths
    /*----------------------------------------------------------------------*/

    @Test
    public void flattenedAttributesCarryDeepScimPath() {
        var restSchema = translate(true, true, true, true, userResource());
        var user = restSchema.objectClass("User");

        assertEquals(findAttr(user, "name_formatted").scim().path(),
                AttributePath.of("name", "formatted"));
        assertEquals(findAttr(user, "work_email").scim().path(),
                AttributePath.of("emails").valueFilter("type", "work").child("value"));
        assertEquals(findAttr(user, "other_phone").scim().path(),
                AttributePath.of("phoneNumbers").valueFilter("type", "other").child("value"));
        assertEquals(findAttr(user, "work_address_locality").scim().path(),
                AttributePath.of("addresses").valueFilter("type", "work").child("locality"));
    }

    /*----------------------------------------------------------------------*/
    /* Value mapping: read (flatten) and write (deflatten) round trip
    /*----------------------------------------------------------------------*/

    @Test
    public void flattenedAttributeReadsNestedScimValue() {
        var restSchema = translate(true, true, false, false, userResource());
        var user = restSchema.objectClass("User");
        var nameMapping = findAttr(user, "name_formatted").scim();
        var emailMapping = findAttr(user, "work_email").scim();

        var objectNode = (ObjectNode) MAPPER.readTree("""
                { "id": "1",
                  "name": { "formatted": "John Doe", "familyName": "Doe" },
                  "emails": [ { "type": "work", "value": "john@example.com" },
                              { "type": "home", "value": "john@home.example" } ] }
                """);

        assertEquals(nameMapping.valuesFromObject(objectNode), List.of("John Doe"));
        assertEquals(emailMapping.valuesFromObject(objectNode), List.of("john@example.com"));
    }

    @Test
    public void typeBasedAttributeWritesIntoMatchingArrayEntry() {
        var restSchema = translate(false, true, true, false, userResource());
        var user = restSchema.objectClass("User");
        var workEmail = findAttr(user, "work_email").scim();
        var homeEmail = findAttr(user, "home_email").scim();
        var workPhone = findAttr(user, "work_phone").scim();

        var objectNode = MAPPER.createObjectNode();
        workEmail.toJsonNode(AttributeBuilder.build("work_email", "john@example.com"), objectNode);
        homeEmail.toJsonNode(AttributeBuilder.build("home_email", "john@home.example"), objectNode);
        workPhone.toJsonNode(AttributeBuilder.build("work_phone", "+1 555"), objectNode);

        assertEquals(objectNode.at("/emails/0/type").asText(), "work");
        assertEquals(objectNode.at("/emails/0/value").asText(), "john@example.com");
        assertEquals(objectNode.at("/emails/1/type").asText(), "home");
        assertEquals(objectNode.at("/emails/1/value").asText(), "john@home.example");
        assertEquals(objectNode.at("/phoneNumbers/0/type").asText(), "work");
        assertEquals(objectNode.at("/phoneNumbers/0/value").asText(), "+1 555");
        assertFalse(objectNode.has("work_email"), "flat keys must not leak into the payload");

        // the round trip: what was written is read back
        assertEquals(workEmail.valuesFromObject(objectNode), List.of("john@example.com"));
        assertEquals(homeEmail.valuesFromObject(objectNode), List.of("john@home.example"));
    }

    @Test
    public void addressSubAttributeWritesIntoMatchingArrayEntry() {
        var restSchema = translate(false, false, false, true, userResource());
        var user = restSchema.objectClass("User");
        var locality = findAttr(user, "work_address_locality").scim();
        var postalCode = findAttr(user, "work_address_postalCode").scim();

        var objectNode = MAPPER.createObjectNode();
        locality.toJsonNode(AttributeBuilder.build("work_address_locality", "Paris"), objectNode);
        postalCode.toJsonNode(AttributeBuilder.build("work_address_postalCode", "75001"), objectNode);

        assertEquals(objectNode.at("/addresses/0/type").asText(), "work");
        assertEquals(objectNode.at("/addresses/0/locality").asText(), "Paris");
        assertEquals(objectNode.at("/addresses/0/postalCode").asText(), "75001");
        assertFalse(objectNode.has("work_address_locality"), "flat keys must not leak into the payload");

        assertEquals(locality.valuesFromObject(objectNode), List.of("Paris"));
        assertEquals(postalCode.valuesFromObject(objectNode), List.of("75001"));
    }

    @Test
    public void deflattenMergesIntoExistingNestedObject() {
        var restSchema = translate(true, false, false, false, userResource());
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
        var mapping = new ScimAttributeMapping(
                AttributePathDeclaration.of(JavaPathFormat.INSTANCE, AttributePath.of("userName")),
                OpenApiValueMapping.from("string", null));

        var objectNode = MAPPER.createObjectNode();
        mapping.toJsonNode(AttributeBuilder.build("userName", "jdoe"), objectNode);

        assertEquals(objectNode.at("/userName").asText(), "jdoe");
    }

    @Test
    public void terminalValueFilterPathKeepsDefaultWriteBehavior() {
        // a filter that is the last path component (not bracketed by <array> and <field>) is not a
        // filtered-entry write; it must fall back to the default behavior instead of failing
        // with an out-of-bounds access on the path components
        var mapping = new ScimAttributeMapping(
                AttributePathDeclaration.of(JavaPathFormat.INSTANCE,
                        AttributePath.of("emails").valueFilter("type", "work")),
                OpenApiValueMapping.from("string", null));

        var objectNode = MAPPER.createObjectNode();
        mapping.toJsonNode(AttributeBuilder.build("work_email", "john@example.com"), objectNode);

        assertEquals(objectNode.at("/emails").asText(), "john@example.com");
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
