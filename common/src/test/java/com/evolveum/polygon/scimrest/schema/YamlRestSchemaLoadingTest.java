/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.schema;

import com.evolveum.polygon.conndev.api.AttributePath;
import com.evolveum.polygon.conndev.api.BasicJsonPathFormat;
import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.api.ParsingException;
import com.evolveum.polygon.conndev.yaml.YamlSchemaLoader;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectReference;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.testng.annotations.Test;
import tools.jackson.databind.node.JsonNodeFactory;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.expectThrows;
import static org.testng.Assert.assertTrue;

/**
 * The SCIM/REST schema front-end is driven by the location-aware engine against a live
 * {@link RestSchemaBuilderImpl}: SCIM mapping blocks ({@code scim:}) and the SCIM-specific
 * {@code nativeType} key bind onto the real builders, and the {@code DefinitionValue}s carry the
 * YAML location. Building yields a real {@link RestSchema}, not the inert conndev {@code BaseSchema}.
 */
public class YamlRestSchemaLoadingTest {

    private static final class StubConnector implements Connector {
        @Override public Configuration getConfiguration() { return null; }
        @Override public void init(Configuration c) { }
        @Override public void dispose() { }
    }

    @Test
    public void yamlSchemaBindsTheScimMappings() {
        var builder = new RestSchemaBuilderImpl(StubConnector.class, ContextLookup.none());
        var loader = new YamlSchemaLoader(builder);
        loader.load("""
                objectClasses:
                  User:
                    description: a user
                    scim:
                      name: user
                      schemaUri: urn:ietf:params:scim:schemas:core:2.0:User
                    attributes:
                      email:
                        jsonType: string
                        scim:
                          name: emails
                          type: email
                        connId:
                          name: __UID__
                """);

        // the SCIM object-class mapping was applied by the engine
        var user = builder.objectClass("User");
        assertEquals(user.scim().name(), "user");
        assertEquals(user.scim().schemaUri(), "urn:ietf:params:scim:schemas:core:2.0:User");

        // the SCIM attribute mapping was applied
        var email = user.attribute("email");
        assertEquals(email.scim().name(), "emails");
        assertEquals(email.scim().type(), "email");

        // the connId name DV carries the YAML location (line 14, column 11)
        assertEquals(email.connId().name().location().name(), "inline document");
        assertEquals(email.connId().name().location().line(), 14);
        assertEquals(email.connId().name().location().column(), 11);
    }

    @Test
    public void buildingYieldsALiveRestSchema() {
        var builder = new RestSchemaBuilderImpl(StubConnector.class, ContextLookup.none());
        var loader = new YamlSchemaLoader(builder);
        loader.load("""
                objectClasses:
                  User:
                    attributes:
                      id:
                        jsonType: string
                        connId:
                          name: __UID__
                """);

        // building yields a real RestSchema (not the inert conndev BaseSchema)
        assertTrue(loader.build() instanceof RestSchema);
    }

    @Test
    public void scimPathIsBoundWithTheScimFormat() {
        var builder = new RestSchemaBuilderImpl(StubConnector.class, ContextLookup.none());
        var loader = new YamlSchemaLoader(builder);
        loader.load("""
                objectClasses:
                  User:
                    attributes:
                      givenName:
                        scim:
                          path: name.givenName
                """);

        var declaration = builder.objectClass("User").attribute("givenName").scim().path();

        assertEquals(declaration.type().value(), ScimPathFormat.INSTANCE);
        assertEquals(declaration.value().value(), "name.givenName");
        assertEquals(declaration.value().location().line(), 6);
        assertEquals(declaration.value().location().column(), 11);
        // the expression parses lazily to the expected path
        assertEquals(declaration.actual(), AttributePath.of("name", "givenName"));
    }

    @Test
    public void scimPathBindsValueFiltersAndExtensionUris() {
        var builder = new RestSchemaBuilderImpl(StubConnector.class, ContextLookup.none());
        var loader = new YamlSchemaLoader(builder);
        loader.load("""
                objectClasses:
                  User:
                    attributes:
                      primaryEmail:
                        scim:
                          path: emails[primary eq true].value
                      employeeNumber:
                        scim:
                          path: urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:employeeNumber
                """);

        var user = builder.objectClass("User");
        assertEquals(user.attribute("primaryEmail").scim().path().actual(),
                AttributePath.of("emails").valueFilter("primary", Boolean.TRUE).child("value"));
        assertEquals(user.attribute("employeeNumber").scim().path().actual(),
                AttributePath.of(
                        new AttributePath.Extension("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User"),
                        new AttributePath.Attribute("employeeNumber")));
    }

    @Test
    public void scimPathAcceptsTheExplicitTypeValueMapping() {
        var builder = new RestSchemaBuilderImpl(StubConnector.class, ContextLookup.none());
        var loader = new YamlSchemaLoader(builder);
        loader.load("""
                objectClasses:
                  User:
                    attributes:
                      id:
                        scim:
                          path:
                            type: SCIM_PATH
                            value: id
                """);

        var declaration = builder.objectClass("User").attribute("id").scim().path();

        assertEquals(declaration.type().value(), ScimPathFormat.INSTANCE);
        assertEquals(declaration.value().value(), "id");
    }

    @Test
    public void invalidScimPathFailsAtBuildNamingTheExpression() {
        var builder = new RestSchemaBuilderImpl(StubConnector.class, ContextLookup.none());
        var loader = new YamlSchemaLoader(builder);
        loader.load("""
                objectClasses:
                  User:
                    attributes:
                      email:
                        scim:
                          type: string
                          path: emails[primary ne true].value
                """);

        // the structural rules force the mapping build, which forces the lazy path parse
        var exception = expectThrows(ParsingException.class, builder::applyStructuralRules);

        assertTrue(exception.getMessage().contains("emails[primary ne true].value"), exception.getMessage());
    }

    @Test
    public void scimFlattenBindsThePerObjectClassFlattenList() {
        var builder = new RestSchemaBuilderImpl(StubConnector.class, ContextLookup.none());
        var loader = new YamlSchemaLoader(builder);
        loader.load("""
                objectClasses:
                  User:
                    scim:
                      name: user
                      flatten:
                        - name
                        - emails
                  Group:
                    scim:
                      flatten: name
                """);

        assertEquals(builder.objectClass("User").scim().flattenAttributes(), List.of("name", "emails"));
        // the scalar form binds a single attribute
        assertEquals(builder.objectClass("Group").scim().flattenAttributes(), List.of("name"));
    }

    @Test
    public void scimFlattenRejectsAMappingValue() {
        var builder = new RestSchemaBuilderImpl(StubConnector.class, ContextLookup.none());
        var loader = new YamlSchemaLoader(builder);

        var exception = expectThrows(IllegalArgumentException.class, () -> loader.load("""
                objectClasses:
                  User:
                    scim:
                      flatten:
                        emails: true
                """));

        assertTrue(exception.getMessage().contains("flatten"), exception.getMessage());
    }

    @Test
    public void scimExtensionsBindTheExtensionFlattenMapping() {
        var builder = new RestSchemaBuilderImpl(StubConnector.class, ContextLookup.none());
        var loader = new YamlSchemaLoader(builder);
        loader.load("""
                objectClasses:
                  User:
                    scim:
                      extensions:
                        enterprise:
                          uri: "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User"
                          flatten:
                            - photos
                            - department
                        slack:
                          uri: "urn:ietf:params:scim:schemas:extension:slack:profile:2.0:User"
                          flatten: teamPhotos
                        costcenter: "urn:test:CostCenter"
                """);

        var scim = builder.objectClass("User").scim();
        assertEquals(scim.extensionFlattens().size(), 2, "the URI-only extension carries no flatten list");
        var enterprise = scim.extensionFlattens().getFirst();
        assertEquals(enterprise.alias(), "enterprise");
        assertEquals(enterprise.extensionUri(), "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User");
        assertEquals(enterprise.flatten(), List.of("photos", "department"));
        // the scalar form binds a single attribute
        assertEquals(scim.extensionFlattens().get(1).flatten(), List.of("teamPhotos"));

        assertEquals(scim.extensionUriFromAlias("enterprise"),
                "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User");
        assertEquals(scim.extensionUriFromAlias("costcenter"), "urn:test:CostCenter");
    }

    @Test
    public void scimExtensionsRejectAFlattenSequenceOfMappings() {
        var builder = new RestSchemaBuilderImpl(StubConnector.class, ContextLookup.none());
        var loader = new YamlSchemaLoader(builder);

        var exception = expectThrows(IllegalArgumentException.class, () -> loader.load("""
                objectClasses:
                  User:
                    scim:
                      extensions:
                        enterprise:
                          uri: "urn:test:Enterprise"
                          flatten:
                            photos: true
                """));

        assertTrue(exception.getMessage().contains("flatten"), exception.getMessage());
    }

    @Test
    public void scimExtensionsRejectAMissingUri() {
        var builder = new RestSchemaBuilderImpl(StubConnector.class, ContextLookup.none());
        var loader = new YamlSchemaLoader(builder);

        var exception = expectThrows(IllegalArgumentException.class, () -> loader.load("""
                objectClasses:
                  User:
                    scim:
                      extensions:
                        enterprise:
                          flatten: photos
                """));

        assertTrue(exception.getMessage().contains("uri"), exception.getMessage());
    }

    /**
     * The issue shape (MID #12495): a reference attribute with a {@code json: implementation:
     * deserialize: |} block — the declarative counterpart of the Groovy
     * {@code json { implementation { deserialize { ... } } }} DSL. The fragment carries its own
     * imports (hoisted out of the compiled closure), and the built mapping converts the wire node
     * through the closure into a {@code ConnectorObjectReference}.
     */
    @Test
    public void referenceJsonImplementationBlockBindsAndBuilds() {
        var builder = new RestSchemaBuilderImpl(StubConnector.class, ContextLookup.none());
        var loader = new YamlSchemaLoader(builder);
        loader.load("""
                objectClasses:
                  Membership:
                    embedded: true
                    references:
                      project:
                        objectClass: Project
                        json:
                          type: string
                          openApiFormat: uri-reference
                          path: $._links.project
                          implementation:
                            deserialize: |
                              import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder
                              import org.identityconnectors.framework.common.objects.ConnectorObjectReference
                              import org.identityconnectors.framework.common.objects.ObjectClass
                              var href = value.get("href")?.asText()
                              var pid = href.substring(href.lastIndexOf("/") + 1)
                              var obj = new ConnectorObjectBuilder()
                                      .setObjectClass(new ObjectClass("Project"))
                                      .setUid(pid)
                                      .setName(value.get("title")?.asText())
                              return new ConnectorObjectReference(obj.build())
                """);

        var schema = loader.build();
        var mapping = schema.objectClass("Membership").attributeFromProtocolName("project").json();
        var sample = JsonNodeFactory.instance.objectNode()
                .set("_links", JsonNodeFactory.instance.objectNode()
                        .set("project", JsonNodeFactory.instance.objectNode()
                                .set("href", JsonNodeFactory.instance.textNode("https://op.example.org/api/v3/projects/123"))
                                .set("title", JsonNodeFactory.instance.textNode("Proj"))));

        var connId = mapping.singleValueFromAttribute(mapping.attributeFromObject(sample));

        assertTrue(connId instanceof ConnectorObjectReference,
                "expected a ConnectorObjectReference, got " + connId);
        var ref = (ConnectorObject) ((ConnectorObjectReference) connId).getValue();
        assertEquals(ref.getUid(), new Uid("123"));
        assertEquals(ref.getName(), new Name("Proj"));
        // a reference attribute is presented to ConnId as a ConnectorObjectReference
        assertEquals(schema.objectClass("Membership").attributeFromProtocolName("project").connId().getType(),
                ConnectorObjectReference.class);
    }

    /**
     * A {@code type} declared after the {@code implementation} block in document order still feeds
     * the lazy base mapping — the Groovy DSL is order-independent the same way.
     */
    @Test
    public void jsonImplementationBeforeTypeStillResolvesTheBase() {
        var builder = new RestSchemaBuilderImpl(StubConnector.class, ContextLookup.none());
        var loader = new YamlSchemaLoader(builder);
        loader.load("""
                objectClasses:
                  Widget:
                    attributes:
                      label:
                        json:
                          implementation:
                            deserialize: |
                              return "d:" + value.asText()
                            serialize: |
                              return "s:" + value
                          type: string
                """);

        var mapping = loader.build().objectClass("Widget").attributeFromProtocolName("label").json();

        assertEquals(mapping.singleValueFromAttribute(JsonNodeFactory.instance.stringNode("x")), "d:x");
    }

    /** A typo'd sub-key inside the {@code implementation} block fails fast, naming the key. */
    @Test
    public void unknownKeyInsideJsonImplementationFailsFast() {
        var builder = new RestSchemaBuilderImpl(StubConnector.class, ContextLookup.none());
        var loader = new YamlSchemaLoader(builder);

        var exception = expectThrows(IllegalArgumentException.class, () -> loader.load("""
                objectClasses:
                  Widget:
                    attributes:
                      label:
                        json:
                          type: string
                          implementation:
                            deserialise: |
                              return value
                """));

        assertTrue(exception.getMessage().contains("deserialise"), exception.getMessage());
    }

    @Test
    public void jsonPathIsInheritedFromTheBaseBinding() {
        var builder = new RestSchemaBuilderImpl(StubConnector.class, ContextLookup.none());
        var loader = new YamlSchemaLoader(builder);
        loader.load("""
                objectClasses:
                  User:
                    attributes:
                      city:
                        json:
                          type: string
                          path: $.address.city
                """);

        builder.applyStructuralRules();
        var city = loader.build().objectClass("User").attributeFromProtocolName("city");

        assertEquals(city.json().pathDeclaration().type().value(), BasicJsonPathFormat.INSTANCE);
        assertEquals(city.json().path().components(), List.of(
                new AttributePath.Attribute("address"),
                new AttributePath.Attribute("city")));
    }
}
