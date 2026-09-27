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
import com.evolveum.polygon.scimrest.schema.RestAttributeDefinition;
import com.evolveum.polygon.scimrest.schema.RestObjectClassDefinition;
import com.evolveum.polygon.scimrest.schema.RestSchema;
import com.evolveum.polygon.scimrest.schema.RestSchemaBuilderImpl;
import com.evolveum.polygon.scimrest.schema.ScimAttributeMapping;
import com.unboundid.scim2.common.types.AttributeDefinition;
import com.unboundid.scim2.common.types.ResourceTypeResource;
import com.unboundid.scim2.common.types.SchemaResource;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
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
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.expectThrows;
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
        return translate(new TestConfig(name, emails, phones, addresses), Map.of(), resources);
    }

    /**
     * @param flattenByObjectClass the {@code scim { flatten ... }} list configured per object
     *        class (the per-object-class extension of the connector-level configuration)
     */
    private static RestSchema translate(ScimClientConfiguration configuration,
                                        Map<String, List<String>> flattenByObjectClass,
                                        ScimResourceContext... resources) {
        var builder = new RestSchemaBuilderImpl(StubConnector.class, null);
        flattenByObjectClass.forEach((objectClass, families) -> {
            var scim = builder.objectClass(objectClass).scim();
            families.forEach(scim::flatten);
        });
        var translator = new ScimSchemaTranslator(null, configuration);
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

    /** A resource carrying non well-known complex attributes: a single-valued one, a
     *  multi-valued one with a {@code type} discriminator, a multi-valued one without one, and a scalar. */
    private static ScimResourceContext genericResource() {
        var manager = complex("manager", List.of(
                scalar("displayName", AttributeDefinition.Type.STRING, false),
                scalar("title", AttributeDefinition.Type.STRING, false)
        ));
        var photos = complexMulti("photos", List.of(
                scalar("uri", AttributeDefinition.Type.STRING, false),
                scalar("value", AttributeDefinition.Type.STRING, false),
                scalar("type", AttributeDefinition.Type.STRING, false),
                scalar("primary", AttributeDefinition.Type.BOOLEAN, false)
        ));
        var notes = complexMulti("notes", List.of(
                scalar("value", AttributeDefinition.Type.STRING, false)
        ));
        var schema = new SchemaResource("urn:test:Generic", "Generic", "Generic",
                List.of(manager, photos, notes, scalar("status", AttributeDefinition.Type.STRING, false)));
        var resourceType = new ResourceTypeResource("Generic", "Generic", "Generic resource",
                URI.create("http://localhost/Generic"), URI.create("urn:test:Generic"), List.of());
        return new ScimResourceContext(resourceType, "/Generic", schema, new HashMap<>());
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
    /* Per object class configuration (scim { flatten ... })
    /*----------------------------------------------------------------------*/

    @Test
    public void perObjectClassFlattenEnablesFamilyGloballyDisabled() {
        var restSchema = translate(new TestConfig(false, false, false, false),
                Map.of("User", List.of("emails")), userResource());
        var user = restSchema.objectClass("User");

        assertNotNull(findAttr(user, "work_email"));
        assertNull(user.attributeFromProtocolName("emails"), "the complex attribute itself must not exist");
        assertNull(restSchema.objectClass("User__emails"), "no embedded class for a flattened attribute");
        assertEquals(findAttr(user, "name").connId().getType(), EmbeddedObject.class, "unlisted families follow the global configuration");
        assertNotNull(restSchema.objectClass("User__name"));
    }

    @Test
    public void perObjectClassFlattenIsAdditiveToGlobalConfiguration() {
        // global on: the object-class list adds nothing new — every family is flattened
        var restSchema = translate(new TestConfig(true, true, true, true),
                Map.of("User", List.of("name")), userResource());
        var user = restSchema.objectClass("User");

        assertNotNull(findAttr(user, "name_formatted"));
        assertNotNull(findAttr(user, "work_email"));
        assertNotNull(findAttr(user, "work_phone"));
        assertNotNull(findAttr(user, "work_address_locality"));

        // global off: the list enables the listed family only
        var restSchema2 = translate(new TestConfig(false, false, false, false),
                Map.of("User", List.of("name")), userResource());
        var user2 = restSchema2.objectClass("User");

        assertNotNull(findAttr(user2, "name_formatted"));
        assertEquals(findAttr(user2, "emails").connId().getType(), EmbeddedObject.class,
                "unlisted families follow the global configuration");
    }

    @Test
    public void perObjectClassFlattenAppliesOnlyToItsObjectClass() {
        var builder = new RestSchemaBuilderImpl(StubConnector.class, null);
        builder.objectClass("User").scim().flatten("emails");
        var translator = new ScimSchemaTranslator(null, new TestConfig(false, false, false, false));
        var user = userResource();
        var generic = genericResource();
        translator.correlateObjectClasses(user, builder);
        translator.correlateObjectClasses(generic, builder);
        translator.populateSchema(user, builder);
        translator.populateSchema(generic, builder);
        for (var info : ConnDevSchema.objectClassInfos()) {
            builder.defineObjectClass(info);
        }
        var restSchema = builder.build();

        var userClass = restSchema.objectClass("User");
        assertNotNull(findAttr(userClass, "work_email"));
        assertEquals(findAttr(userClass, "name").connId().getType(), EmbeddedObject.class);

        var genericClass = restSchema.objectClass("Generic");
        assertEquals(findAttr(genericClass, "photos").connId().getType(), EmbeddedObject.class,
                "the User flatten list must not leak into Generic");
    }

    @Test
    public void genericSingleValuedComplexAttributeIsFlattened() {
        var restSchema = translate(new TestConfig(false, false, false, false),
                Map.of("Generic", List.of("manager")), genericResource());
        var generic = restSchema.objectClass("Generic");

        assertEquals(findAttr(generic, "manager_displayName").scim().path(),
                AttributePath.of("manager", "displayName"));
        assertEquals(findAttr(generic, "manager_title").scim().path(),
                AttributePath.of("manager", "title"));
        assertEquals(findAttr(generic, "manager_displayName").connId().getType(), String.class);
        assertNull(generic.attributeFromProtocolName("manager"), "the complex attribute itself must not exist");
        assertNull(restSchema.objectClass("Generic__manager"), "no embedded class for a flattened attribute");
    }

    @Test
    public void genericMultiValuedComplexAttributeIsFlattenedByType() {
        var restSchema = translate(new TestConfig(false, false, false, false),
                Map.of("Generic", List.of("photos")), genericResource());
        var generic = restSchema.objectClass("Generic");

        // value sub-attribute: <type>_<singular>; other scalar sub-attributes: <type>_<singular>_<sub>
        assertEquals(findAttr(generic, "work_photo").scim().path(),
                AttributePath.of("photos").valueFilter("type", "work").child("value"));
        assertEquals(findAttr(generic, "other_photo_uri").scim().path(),
                AttributePath.of("photos").valueFilter("type", "other").child("uri"));
        assertNotNull(findAttr(generic, "home_photo_primary"));
        assertNull(generic.attributeFromProtocolName("photos"), "the complex attribute itself must not exist");
        assertNull(restSchema.objectClass("Generic__photos"), "no embedded class for a flattened attribute");
    }

    @Test
    public void flatteningAnAttributeMissingFromTheSchemaFails() {
        var builder = new RestSchemaBuilderImpl(StubConnector.class, null);
        builder.objectClass("Generic").scim().flatten("nonexistent");
        var translator = new ScimSchemaTranslator(null, new TestConfig(false, false, false, false));
        var resource = genericResource();
        translator.correlateObjectClasses(resource, builder);

        var exception = expectThrows(ConfigurationException.class, () -> translator.populateSchema(resource, builder));
        assertTrue(exception.getMessage().contains("nonexistent"), exception.getMessage());
        assertTrue(exception.getMessage().contains("Generic"), exception.getMessage());
    }

    @Test
    public void flatteningANonComplexAttributeFails() {
        var builder = new RestSchemaBuilderImpl(StubConnector.class, null);
        builder.objectClass("Generic").scim().flatten("status");
        var translator = new ScimSchemaTranslator(null, new TestConfig(false, false, false, false));
        var resource = genericResource();
        translator.correlateObjectClasses(resource, builder);

        var exception = expectThrows(ConfigurationException.class, () -> translator.populateSchema(resource, builder));
        assertTrue(exception.getMessage().contains("not a complex attribute"), exception.getMessage());
    }

    @Test
    public void flatteningAMultiValuedAttributeWithoutTypeFails() {
        var builder = new RestSchemaBuilderImpl(StubConnector.class, null);
        builder.objectClass("Generic").scim().flatten("notes");
        var translator = new ScimSchemaTranslator(null, new TestConfig(false, false, false, false));
        var resource = genericResource();
        translator.correlateObjectClasses(resource, builder);

        var exception = expectThrows(ConfigurationException.class, () -> translator.populateSchema(resource, builder));
        assertTrue(exception.getMessage().contains("'type' sub-attribute"), exception.getMessage());
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
