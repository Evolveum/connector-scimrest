/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.unit.groovy;

import com.evolveum.polygon.conndev.groovy.GroovyContext;
import com.evolveum.polygon.conndev.groovy.GroovySchemaLoader;
import com.evolveum.polygon.scimrest.groovy.connector.AbstractGroovyRestConnector;
import com.evolveum.polygon.scimrest.schema.RestSchemaBuilderImpl;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;

/**
 * The per-object-class {@code scim { flatten ... }} configuration binds onto the object-class
 * SCIM mapping from the Groovy schema DSL (the YAML counterpart is covered by
 * {@code YamlRestSchemaLoadingTest#scimFlattenBindsThePerObjectClassFlattenList}).
 */
public class ScimFlattenBindingTest {

    @Test
    public void flattenBindsToTheObjectClassScimMapping() {
        var schema = new RestSchemaBuilderImpl(AbstractGroovyRestConnector.class, null);
        var loader = new GroovySchemaLoader(new GroovyContext(), schema);
        loader.load("""
                objectClass("User") {
                    scim {
                        flatten "name"
                        flatten "emails"
                    }
                }
                objectClass("Group") {
                    scim {
                        flatten "phoneNumbers"
                    }
                }
                """);

        assertEquals(schema.objectClass("User").scim().flattenAttributes(), List.of("name", "emails"));
        assertEquals(schema.objectClass("Group").scim().flattenAttributes(), List.of("phoneNumbers"));
        assertEquals(schema.objectClass("User").scim().name(), "User");
    }

    @Test
    public void extensionBlockBindsAliasNamespaceAndFlattenList() {
        var schema = new RestSchemaBuilderImpl(AbstractGroovyRestConnector.class, null);
        var loader = new GroovySchemaLoader(new GroovyContext(), schema);
        loader.load("""
                objectClass("User") {
                    scim {
                        extension("enterprise", "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User") {
                            flatten "photos"
                            flatten "department"
                        }
                        extension("costcenter", "urn:test:CostCenter")
                    }
                }
                """);

        var scim = schema.objectClass("User").scim();
        assertEquals(scim.extensionFlattens().size(), 1,
                "only extensions with a non-empty flatten list are reported");
        var flattening = scim.extensionFlattens().getFirst();
        assertEquals(flattening.alias(), "enterprise");
        assertEquals(flattening.extensionUri(), "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User");
        assertEquals(flattening.flatten(), List.of("photos", "department"));

        // both declaration forms resolve the alias to its namespace
        assertEquals(scim.extensionUriFromAlias("enterprise"),
                "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User");
        assertEquals(scim.extensionUriFromAlias("costcenter"), "urn:test:CostCenter");
    }
}
