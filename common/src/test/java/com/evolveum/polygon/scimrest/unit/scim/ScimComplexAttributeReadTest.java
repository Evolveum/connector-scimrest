/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.unit.scim;

import com.evolveum.polygon.conndev.dev.ConnDevSchema;
import com.evolveum.polygon.scimrest.impl.scim.ScimResourceContext;
import com.evolveum.polygon.scimrest.impl.scim.ScimEmbeddedObjectValueMapping;
import com.evolveum.polygon.scimrest.impl.scim.ScimSchemaTranslator;
import com.evolveum.polygon.scimrest.schema.MappedObjectClass;
import com.evolveum.polygon.scimrest.schema.RestAttributeDefinition;
import com.evolveum.polygon.scimrest.schema.RestSchema;
import com.evolveum.polygon.scimrest.schema.RestSchemaBuilderImpl;
import com.unboundid.scim2.common.types.AttributeDefinition;
import com.unboundid.scim2.common.types.ResourceTypeResource;
import com.unboundid.scim2.common.types.SchemaResource;
import org.identityconnectors.framework.common.objects.EmbeddedObject;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import tools.jackson.databind.node.ObjectNode;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;

import static org.testng.Assert.*;

/**
 * Tests automatic detection of SCIM complex attributes and their mapping to EmbeddedObject.
 */
public class ScimComplexAttributeReadTest {

    private static final class StubConnector implements Connector {
        @Override public Configuration getConfiguration() { return null; }
        @Override public void init(Configuration c) { }
        @Override public void dispose() { }
    }

    private static RestSchema translate(ScimResourceContext... resources) {
        var builder = new RestSchemaBuilderImpl(StubConnector.class, null);
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

    private static ScimResourceContext resource(String name, String endpoint,
                                                String urn, SchemaResource schema) {
        var resourceType = new ResourceTypeResource(name, name, name + " resource",
                URI.create(endpoint), URI.create(urn), List.of());
        return new ScimResourceContext(resourceType, endpoint, schema, new HashMap<>());
    }

    /*----------------------------------------------------------------------*/
    /* Schema structure tests
    /*----------------------------------------------------------------------*/

    @Test
    public void embeddedClassCreatedForComplexAttribute() {
        var nameAttr = complex("name", List.of(
                scalar("formatted", AttributeDefinition.Type.STRING, false),
                scalar("familyName", AttributeDefinition.Type.STRING, false),
                scalar("givenName", AttributeDefinition.Type.STRING, false)
        ));
        var schema = new SchemaResource("urn:test:User", "User", "User", List.of(nameAttr));
        var restSchema = translate(resource("User", "/Users", "urn:test:User", schema));

        assertTrue(restSchema.objectClass("User__name") != null);
    }

    @Test
    public void embeddedClassMarkedAsEmbedded() {
        var nameAttr = complex("name", List.of(
                scalar("givenName", AttributeDefinition.Type.STRING, false)
        ));
        var schema = new SchemaResource("urn:test:User", "User", "User", List.of(nameAttr));
        var restSchema = translate(resource("User", "/Users", "urn:test:User", schema));

        var embedded = restSchema.objectClass("User__name");
        assertEquals(embedded.connId().isEmbedded(), true, "should be embedded");
    }

    @Test
    public void embeddedClassHasCorrectSubAttributes() {
        var nameAttr = complex("name", List.of(
                scalar("formatted", AttributeDefinition.Type.STRING, false),
                scalar("familyName", AttributeDefinition.Type.STRING, false),
                scalar("givenName", AttributeDefinition.Type.STRING, false),
                scalar("middleName", AttributeDefinition.Type.STRING, false)
        ));
        var schema = new SchemaResource("urn:test:User", "User", "User", List.of(nameAttr));
        var restSchema = translate(resource("User", "/Users", "urn:test:User", schema));

        var embedded = restSchema.objectClass("User__name");
        var attrNames = embedded.attributes().stream()
                .map(a -> a.connId().getName())
                .sorted()
                .toList();
        assertEquals(attrNames, List.of("familyName", "formatted", "givenName", "middleName"));
    }

    @Test
    public void subAttributeTypesMatchConnIdType() {
        var emailAttr = complexMulti("emails", List.of(
                scalar("value", AttributeDefinition.Type.STRING, false),
                scalar("type", AttributeDefinition.Type.STRING, false),
                scalar("primary", AttributeDefinition.Type.BOOLEAN, false)
        ));
        var schema = new SchemaResource("urn:test:User", "User", "User", List.of(emailAttr));
        var restSchema = translate(resource("User", "/Users", "urn:test:User", schema));

        var embedded = restSchema.objectClass("User__emails");

        var valAttr = findAttr(embedded, "value");
        assertEquals(valAttr.connId().getType(), String.class, "value should be String");

        var typeAttr = findAttr(embedded, "type");
        assertEquals(typeAttr.connId().getType(), String.class, "type should be String");

        var primaryAttr = findAttr(embedded, "primary");
        assertEquals(primaryAttr.connId().getType(), Boolean.class, "primary should be Boolean");
    }

    @Test
    public void multiValuedComplexAttributeDeclaredAsEmbeddedObject() {
        var emailAttr = complexMulti("emails", List.of(
                scalar("value", AttributeDefinition.Type.STRING, false)
        ));
        var schema = new SchemaResource("urn:test:User", "User", "User", List.of(emailAttr));
        var restSchema = translate(resource("User", "/Users", "urn:test:User", schema));

        var user = restSchema.objectClass("User");
        var emailsAttr = findAttr(user, "emails");
        assertEquals(emailsAttr.connId().getType(), EmbeddedObject.class,
                "emails should be EmbeddedObject");
        assertTrue(emailsAttr.connId().isMultiValued(), "emails should be multi-valued");
    }

    @Test
    public void singleValuedComplexAttributeDeclaredSingleValued() {
        var nameAttr = complex("name", List.of(
                scalar("givenName", AttributeDefinition.Type.STRING, false)
        ));
        var schema = new SchemaResource("urn:test:User", "User", "User", List.of(nameAttr));
        var restSchema = translate(resource("User", "/Users", "urn:test:User", schema));

        var user = restSchema.objectClass("User");
        var nameAttrDef = findAttr(user, "name");
        assertEquals(nameAttrDef.connId().getType(), EmbeddedObject.class);
        assertFalse(nameAttrDef.connId().isMultiValued(), "name should NOT be multi-valued");
    }

    @Test
    public void multipleComplexAttributesCreateDistinctEmbeddedClasses() {
        var nameAttr = complex("name", List.of(
                scalar("givenName", AttributeDefinition.Type.STRING, false)
        ));
        var emailAttr = complexMulti("emails", List.of(
                scalar("value", AttributeDefinition.Type.STRING, true)
        ));
        var phoneAttr = complexMulti("phoneNumbers", List.of(
                scalar("value", AttributeDefinition.Type.STRING, true)
        ));
        var schema = new SchemaResource("urn:test:User", "User", "User",
                List.of(nameAttr, emailAttr, phoneAttr));
        var restSchema = translate(resource("User", "/Users", "urn:test:User", schema));

        assertNotNull(restSchema.objectClass("User__name"));
        assertNotNull(restSchema.objectClass("User__emails"));
        assertNotNull(restSchema.objectClass("User__phoneNumbers"));
    }

    @Test
    public void nestedComplexSubAttributeSkipped() {
        var nameAttr = complex("name", List.of(
                scalar("givenName", AttributeDefinition.Type.STRING, false)
        ));
        var nestedInner = complex("_nestedInner", List.of(
                scalar("nested", AttributeDefinition.Type.STRING, false)
        ));
        var outerWithNested = complex("outer", List.of(
                nestedInner,
                scalar("plain", AttributeDefinition.Type.STRING, false)
        ));
        var schema = new SchemaResource("urn:test:User", "User", "User",
                List.of(nameAttr, outerWithNested));
        var restSchema = translate(resource("User", "/Users", "urn:test:User", schema));

        var outerEmbedded = restSchema.objectClass("User__outer");
        var attrs = outerEmbedded.attributes().stream()
                .map(a -> a.connId().getName())
                .toList();
        assertEquals(attrs, List.of("plain"), "nested complex should be skipped");
    }

    @Test
    public void scimDateTimeTypeMappedToZonedDateTime() {
        var complexAttr = complex("meta", List.of(
                scalar("created", AttributeDefinition.Type.DATETIME, false),
                scalar("updated", AttributeDefinition.Type.DATETIME, false)
        ));
        var schema = new SchemaResource("urn:test:User", "User", "User", List.of(complexAttr));
        var restSchema = translate(resource("User", "/Users", "urn:test:User", schema));

        var metaEmbedded = restSchema.objectClass("User__meta");
        var created = findAttr(metaEmbedded, "created");
    }

    @Test
    public void scimDecimalTypeMappedToBigDecimal() {
        var complexAttr = complex("balance", List.of(
                scalar("amount", AttributeDefinition.Type.DECIMAL, false)
        ));
        var schema = new SchemaResource("urn:test:User", "User", "User", List.of(complexAttr));
        var restSchema = translate(resource("User", "/Users", "urn:test:User", schema));

        var embedded = restSchema.objectClass("User__balance");
    }

    /*----------------------------------------------------------------------*/
    /* Value mapping tests
    /*----------------------------------------------------------------------*/

    @Test
    public void valueMapping_returnsConnIdType() {
        var mapping = new ScimEmbeddedObjectValueMapping(null, "Foo");
        assertEquals(mapping.connIdType(), EmbeddedObject.class);
    }

    @Test
    public void valueMapping_returnsPrimaryWireType() {
        var mapping = new ScimEmbeddedObjectValueMapping(null, "Foo");
        assertEquals(mapping.primaryWireType(), ObjectNode.class);
    }

    /*----------------------------------------------------------------------*/
    /* Helpers
    /*----------------------------------------------------------------------*/

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

    private static RestAttributeDefinition findAttr(MappedObjectClass oc, String name) {
        for (var attr : oc.attributes()) {
            if (name.equals(attr.connId().getName())) {
                return attr;
            }
        }
        fail("Attribute not found: " + name);
        return null;
    }
}
