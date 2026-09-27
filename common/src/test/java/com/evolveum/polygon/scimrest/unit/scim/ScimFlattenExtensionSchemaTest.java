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
 * Schema-level behavior of flattening complex attributes of a mapped SCIM extension
 * ({@code scim { extension(...) { flatten ... } }}): the flat attributes are named after the
 * extension alias and carry SCIM paths qualified with the extension schema URI — and, per
 * RFC 7643, resolve against the resource's top-level JSON, not a key named after the URI.
 */
public class ScimFlattenExtensionSchemaTest {

    private static final String ENTERPRISE_URI = "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User";
    private static final String SLACK_URI = "urn:ietf:params:scim:schemas:extension:slack:profile:2.0:User";

    private static final class StubConnector implements Connector {
        @Override public Configuration getConfiguration() { return null; }
        @Override public void init(Configuration c) { }
        @Override public void dispose() { }
    }

    /** Minimal configuration — the connector-level flatten flags must not apply to extensions. */
    private static final class TestConfig implements ScimClientConfiguration {
        @Override public String getScimBaseUrl() { return null; }
        @Override public Boolean getScimFlattenNameAttribute() { return false; }
        @Override public Boolean getScimFlattenEmails() { return false; }
        @Override public Boolean getScimFlattenPhoneNumbers() { return false; }
        @Override public Boolean getScimFlattenAddresses() { return false; }
    }

    /** A User resource whose enterprise extension carries a single-valued and a
     *  multi-valued (type-discriminated) complex attribute (and a scalar one). */
    private static ScimResourceContext userResource() {
        var schema = new SchemaResource("urn:test:User", "User", "User",
                List.of(scalar("userName", AttributeDefinition.Type.STRING, false)));
        var enterprise = new SchemaResource(ENTERPRISE_URI, "EnterpriseUser", "Enterprise user extension",
                List.of(department(), photos(),
                        scalar("employeeNumber", AttributeDefinition.Type.STRING, false)));
        var resourceType = new ResourceTypeResource("User", "User", "User resource",
                URI.create("http://localhost/Users"), URI.create("urn:test:User"), List.of());
        var extensions = new HashMap<String, SchemaResource>();
        extensions.put(ENTERPRISE_URI, enterprise);
        return new ScimResourceContext(resourceType, "/Users", schema, extensions);
    }

    /** A User resource that also carries {@code photos} in its primary schema (beside the extension's). */
    private static ScimResourceContext userResourceWithPrimaryPhotos() {
        var schema = new SchemaResource("urn:test:User", "User", "User",
                List.of(scalar("userName", AttributeDefinition.Type.STRING, false), photos()));
        var enterprise = new SchemaResource(ENTERPRISE_URI, "EnterpriseUser", "Enterprise user extension",
                List.of(department(), photos()));
        var resourceType = new ResourceTypeResource("User", "User", "User resource",
                URI.create("http://localhost/Users"), URI.create("urn:test:User"), List.of());
        var extensions = new HashMap<String, SchemaResource>();
        extensions.put(ENTERPRISE_URI, enterprise);
        return new ScimResourceContext(resourceType, "/Users", schema, extensions);
    }

    /** A User resource with two extensions (enterprise + a slack one with its own multi-valued complex attribute). */
    private static ScimResourceContext twoExtensionResource() {
        var schema = new SchemaResource("urn:test:User", "User", "User",
                List.of(scalar("userName", AttributeDefinition.Type.STRING, false)));
        var enterprise = new SchemaResource(ENTERPRISE_URI, "EnterpriseUser", "Enterprise user extension",
                List.of(department(), photos()));
        var slack = new SchemaResource(SLACK_URI, "SlackProfile", "Slack profile extension",
                List.of(teamPhotos()));
        var resourceType = new ResourceTypeResource("User", "User", "User resource",
                URI.create("http://localhost/Users"), URI.create("urn:test:User"), List.of());
        var extensions = new HashMap<String, SchemaResource>();
        extensions.put(ENTERPRISE_URI, enterprise);
        extensions.put(SLACK_URI, slack);
        return new ScimResourceContext(resourceType, "/Users", schema, extensions);
    }

    private static RestSchema translate(ScimResourceContext resource,
                                        Map<String, List<String>> extensionsByAlias) {
        return translate(resource, List.of(), extensionsByAlias);
    }

    /**
     * @param primaryFlatten the object-class {@code scim { flatten ... }} list (primary schema)
     * @param extensionsByAlias the extensions declared with a flatten list
     *        ({@code scim { extension(alias, uri) { flatten ... } }})
     */
    private static RestSchema translate(ScimResourceContext resource,
                                        List<String> primaryFlatten,
                                        Map<String, List<String>> extensionsByAlias) {
        var builder = new RestSchemaBuilderImpl(StubConnector.class, null);
        var scim = builder.objectClass("User").scim();
        primaryFlatten.forEach(scim::flatten);
        extensionsByAlias.forEach((alias, attributes) -> {
            var uri = "enterprise".equals(alias) ? ENTERPRISE_URI : "slack".equals(alias) ? SLACK_URI : "urn:test:Unknown";
            scim.extension(alias, uri, attributes);
        });
        var translator = new ScimSchemaTranslator(null, new TestConfig());
        translator.correlateObjectClasses(resource, builder);
        translator.populateSchema(resource, builder);
        for (var info : ConnDevSchema.objectClassInfos()) {
            builder.defineObjectClass(info);
        }
        return builder.build();
    }

    /*----------------------------------------------------------------------*/
    /* Schema structure
    /*----------------------------------------------------------------------*/

    @Test
    public void extensionSingleValuedComplexAttributeIsFlattenedWithAliasPrefix() {
        var restSchema = translate(userResource(), Map.of("enterprise", List.of("department")));
        var user = restSchema.objectClass("User");

        assertEquals(findAttr(user, "enterprise_department_code").scim().path(),
                AttributePath.of(new AttributePath.Extension(ENTERPRISE_URI)).child("department").child("code"));
        assertEquals(findAttr(user, "enterprise_department_name").scim().path(),
                AttributePath.of(new AttributePath.Extension(ENTERPRISE_URI)).child("department").child("name"));
        assertEquals(findAttr(user, "enterprise_department_code").connId().getType(), String.class);
        assertFalse(findAttr(user, "enterprise_department_code").connId().isMultiValued());
        assertNull(user.attributeFromProtocolName("department"), "the complex attribute itself must not exist");
        assertNull(restSchema.objectClass("User__department"), "no embedded class for a flattened attribute");
    }

    @Test
    public void extensionMultiValuedComplexAttributeIsFlattenedByType() {
        var restSchema = translate(userResource(), Map.of("enterprise", List.of("photos")));
        var user = restSchema.objectClass("User");

        // value sub-attribute: <alias>_<type>_<singular>; other scalar sub-attributes: <alias>_<type>_<singular>_<sub>
        assertEquals(findAttr(user, "enterprise_work_photo").scim().path(),
                AttributePath.of(new AttributePath.Extension(ENTERPRISE_URI))
                        .child("photos").valueFilter("type", "work").child("value"));
        assertEquals(findAttr(user, "enterprise_other_photo_uri").scim().path(),
                AttributePath.of(new AttributePath.Extension(ENTERPRISE_URI))
                        .child("photos").valueFilter("type", "other").child("uri"));
        assertNotNull(findAttr(user, "enterprise_home_photo_primary"));
        assertEquals(findAttr(user, "enterprise_work_photo").connId().getType(), String.class);
        assertNull(user.attributeFromProtocolName("photos"), "the complex attribute itself must not exist");
    }

    @Test
    public void primaryFlattenNamesStayUnprefixedBesideExtensionFlattens() {
        // the same attribute name flattened from the primary schema and from the extension must
        // yield distinct flat attributes: the primary one unprefixed, the extension one alias-prefixed
        var restSchema = translate(userResourceWithPrimaryPhotos(), List.of("photos"), Map.of("enterprise", List.of("photos")));
        var user = restSchema.objectClass("User");

        assertEquals(findAttr(user, "work_photo").scim().path(),
                AttributePath.of("photos").valueFilter("type", "work").child("value"));
        assertEquals(findAttr(user, "enterprise_work_photo").scim().path(),
                AttributePath.of(new AttributePath.Extension(ENTERPRISE_URI))
                        .child("photos").valueFilter("type", "work").child("value"));
    }

    @Test
    public void eachExtensionFlattensFromItsOwnSchemaWithItsOwnAlias() {
        var restSchema = translate(twoExtensionResource(),
                Map.of("enterprise", List.of("department"), "slack", List.of("teamPhotos")));
        var user = restSchema.objectClass("User");

        assertEquals(findAttr(user, "enterprise_department_code").scim().path(),
                AttributePath.of(new AttributePath.Extension(ENTERPRISE_URI)).child("department").child("code"));
        assertEquals(findAttr(user, "slack_work_teamPhoto").scim().path(),
                AttributePath.of(new AttributePath.Extension(SLACK_URI))
                        .child("teamPhotos").valueFilter("type", "work").child("value"));
        assertNotNull(findAttr(user, "slack_home_teamPhoto"),
                "the 'value' sub-attribute flattens to <alias>_<type>_<singular> without a 'value' suffix");
    }

    @Test
    public void extensionFlatteningIsNotGatedByOnlyExplicitlyListed() {
        var builder = new RestSchemaBuilderImpl(StubConnector.class, null);
        var scim = builder.objectClass("User").scim();
        scim.onlyExplicitlyListed(true);
        scim.extension("enterprise", ENTERPRISE_URI, List.of("department"));
        var translator = new ScimSchemaTranslator(null, new TestConfig());
        var resource = userResource();
        translator.correlateObjectClasses(resource, builder);
        translator.populateSchema(resource, builder);
        for (var info : ConnDevSchema.objectClassInfos()) {
            builder.defineObjectClass(info);
        }
        var restSchema = builder.build();

        assertNotNull(findAttr(restSchema.objectClass("User"), "enterprise_department_code"),
                "the explicit flatten list is user intent, like a scim { path ... } mapping");
    }

    /*----------------------------------------------------------------------*/
    /* Errors
    /*----------------------------------------------------------------------*/

    @Test
    public void flatteningAnUnknownExtensionFails() {
        var builder = new RestSchemaBuilderImpl(StubConnector.class, null);
        builder.objectClass("User").scim().extension("nope", "urn:test:Unknown", List.of("department"));
        var translator = new ScimSchemaTranslator(null, new TestConfig());
        var resource = userResource();
        translator.correlateObjectClasses(resource, builder);

        var exception = expectThrows(ConfigurationException.class, () -> translator.populateSchema(resource, builder));
        assertTrue(exception.getMessage().contains("not part of resource"), exception.getMessage());
        assertTrue(exception.getMessage().contains("urn:test:Unknown"), exception.getMessage());
    }

    @Test
    public void flatteningAnAttributeMissingFromTheExtensionFails() {
        var builder = new RestSchemaBuilderImpl(StubConnector.class, null);
        builder.objectClass("User").scim().extension("enterprise", ENTERPRISE_URI, List.of("nonexistent"));
        var translator = new ScimSchemaTranslator(null, new TestConfig());
        var resource = userResource();
        translator.correlateObjectClasses(resource, builder);

        var exception = expectThrows(ConfigurationException.class, () -> translator.populateSchema(resource, builder));
        assertTrue(exception.getMessage().contains("from extension 'enterprise'"), exception.getMessage());
        assertTrue(exception.getMessage().contains("nonexistent"), exception.getMessage());
    }

    @Test
    public void flatteningAScalarExtensionAttributeFails() {
        var builder = new RestSchemaBuilderImpl(StubConnector.class, null);
        builder.objectClass("User").scim().extension("enterprise", ENTERPRISE_URI, List.of("employeeNumber"));
        var translator = new ScimSchemaTranslator(null, new TestConfig());
        var resource = userResource();
        translator.correlateObjectClasses(resource, builder);

        var exception = expectThrows(ConfigurationException.class, () -> translator.populateSchema(resource, builder));
        assertTrue(exception.getMessage().contains("not a complex attribute"), exception.getMessage());
    }

    @Test
    public void flatteningAMultiValuedExtensionAttributeWithoutTypeFails() {
        var builder = new RestSchemaBuilderImpl(StubConnector.class, null);
        builder.objectClass("User").scim().extension("enterprise", ENTERPRISE_URI, List.of("notes"));
        var translator = new ScimSchemaTranslator(null, new TestConfig());
        var resource = extensionResourceWithNotes();
        translator.correlateObjectClasses(resource, builder);

        var exception = expectThrows(ConfigurationException.class, () -> translator.populateSchema(resource, builder));
        assertTrue(exception.getMessage().contains("'type' sub-attribute"), exception.getMessage());
    }

    @Test
    public void flatteningIntoAnAlreadyDefinedAttributeFails() {
        var builder = new RestSchemaBuilderImpl(StubConnector.class, null);
        var scim = builder.objectClass("User").scim();
        scim.extension("enterprise", ENTERPRISE_URI, List.of("photos"));
        builder.objectClass("User").attribute("enterprise_work_photo").scim().name("employeeNumber");
        var translator = new ScimSchemaTranslator(null, new TestConfig());
        var resource = userResource();
        translator.correlateObjectClasses(resource, builder);

        var exception = expectThrows(ConfigurationException.class, () -> translator.populateSchema(resource, builder));
        assertTrue(exception.getMessage().contains("enterprise_work_photo"), exception.getMessage());
        assertTrue(exception.getMessage().contains("already defined"), exception.getMessage());
    }

    /*----------------------------------------------------------------------*/
    /* Wire shape: extension attributes resolve at the resource's top level (RFC 7643)
    /*----------------------------------------------------------------------*/

    @Test
    public void extensionQualifiedPathReadsTopLevelJson() {
        var filtered = mapping(AttributePath.of(new AttributePath.Extension(ENTERPRISE_URI))
                .child("photos").valueFilter("type", "work").child("value"));
        var structural = mapping(AttributePath.of(new AttributePath.Extension(ENTERPRISE_URI))
                .child("department").child("code"));

        var objectNode = (ObjectNode) MAPPER.readTree("""
                { "id": "1",
                  "department": { "code": "ENG", "name": "Engineering" },
                  "photos": [ { "type": "work", "value": "photo://work", "uri": "http://x" },
                              { "type": "other", "value": "photo://other" } ] }
                """);

        assertEquals(filtered.valuesFromObject(objectNode), List.of("photo://work"));
        assertEquals(structural.valuesFromObject(objectNode), List.of("ENG"));
    }

    @Test
    public void extensionQualifiedPathWritesTopLevelFilteredEntry() {
        var filtered = mapping(AttributePath.of(new AttributePath.Extension(ENTERPRISE_URI))
                .child("photos").valueFilter("type", "work").child("value"));
        var structural = mapping(AttributePath.of(new AttributePath.Extension(ENTERPRISE_URI))
                .child("department").child("code"));

        var objectNode = MAPPER.createObjectNode();
        filtered.toJsonNode(AttributeBuilder.build("enterprise_work_photo", "photo://work"), objectNode);
        structural.toJsonNode(AttributeBuilder.build("enterprise_department_code", "ENG"), objectNode);

        assertEquals(objectNode.at("/photos/0/type").asText(), "work");
        assertEquals(objectNode.at("/photos/0/value").asText(), "photo://work");
        assertEquals(objectNode.at("/department/code").asText(), "ENG");
        assertFalse(objectNode.has(ENTERPRISE_URI), "the extension URI must not become a JSON key");
        assertFalse(objectNode.has("enterprise_work_photo"), "flat keys must not leak into the payload");

        // the round trip: what was written is read back
        assertEquals(filtered.valuesFromObject(objectNode), List.of("photo://work"));
        assertEquals(structural.valuesFromObject(objectNode), List.of("ENG"));
    }

    @Test
    public void unqualifiedPathsKeepTheirWireShape() {
        // a primary-schema path must be unaffected by the extension handling
        var mapping = mapping(AttributePath.of("emails").valueFilter("type", "work").child("value"));
        var objectNode = MAPPER.createObjectNode();
        mapping.toJsonNode(AttributeBuilder.build("work_email", "john@example.com"), objectNode);
        assertEquals(objectNode.at("/emails/0/type").asText(), "work");
        assertEquals(objectNode.at("/emails/0/value").asText(), "john@example.com");
    }

    /*----------------------------------------------------------------------*/
    /* Helpers
    /*----------------------------------------------------------------------*/

    private static final JsonMapper MAPPER = new JsonMapper();

    private static ScimAttributeMapping mapping(AttributePath path) {
        return new ScimAttributeMapping(
                AttributePathDeclaration.of(JavaPathFormat.INSTANCE, path),
                OpenApiValueMapping.from("string", null));
    }

    private static AttributeDefinition scalar(String name, AttributeDefinition.Type type, boolean required) {
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

    private static AttributeDefinition department() {
        return complex("department", List.of(
                scalar("code", AttributeDefinition.Type.STRING, false),
                scalar("name", AttributeDefinition.Type.STRING, false)
        ));
    }

    private static AttributeDefinition photos() {
        return complexMulti("photos", List.of(
                scalar("uri", AttributeDefinition.Type.STRING, false),
                scalar("value", AttributeDefinition.Type.STRING, false),
                scalar("type", AttributeDefinition.Type.STRING, false),
                scalar("primary", AttributeDefinition.Type.BOOLEAN, false)
        ));
    }

    private static AttributeDefinition teamPhotos() {
        return complexMulti("teamPhotos", List.of(
                scalar("value", AttributeDefinition.Type.STRING, false),
                scalar("type", AttributeDefinition.Type.STRING, false)
        ));
    }

    private static ScimResourceContext extensionResourceWithNotes() {
        var schema = new SchemaResource("urn:test:User", "User", "User",
                List.of(scalar("userName", AttributeDefinition.Type.STRING, false)));
        var enterprise = new SchemaResource(ENTERPRISE_URI, "EnterpriseUser", "Enterprise user extension",
                List.of(complexMulti("notes", List.of(scalar("value", AttributeDefinition.Type.STRING, false)))));
        var resourceType = new ResourceTypeResource("User", "User", "User resource",
                URI.create("http://localhost/Users"), URI.create("urn:test:User"), List.of());
        var extensions = new HashMap<String, SchemaResource>();
        extensions.put(ENTERPRISE_URI, enterprise);
        return new ScimResourceContext(resourceType, "/Users", schema, extensions);
    }

    private static RestAttributeDefinition findAttr(RestObjectClassDefinition oc, String name) {
        var attr = oc.attributeFromProtocolName(name);
        assertNotNull(attr, "Attribute not found: " + name);
        return attr;
    }
}
