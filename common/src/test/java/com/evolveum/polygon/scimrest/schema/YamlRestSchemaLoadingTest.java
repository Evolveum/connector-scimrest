/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.schema;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.yaml.YamlSchemaLoader;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
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
}
