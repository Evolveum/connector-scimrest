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
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.testng.annotations.Test;

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
