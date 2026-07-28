/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.unit.groovy;

import com.evolveum.polygon.scimrest.groovy.AbstractGroovyRestConnector;
import com.evolveum.polygon.scimrest.groovy.GroovyContext;
import com.evolveum.polygon.scimrest.groovy.GroovySchemaLoader;
import com.evolveum.polygon.scimrest.schema.RestSchemaBuilderImpl;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * {@code scim { type "boolean" } } (whether written by hand or set behind the scenes by SCIM
 * discovery - {@code ScimSchemaTranslator.populateAttribute()}) now registers into the shared
 * {@code protocolMappings} map, the same way {@code .json()} already does for generic REST
 * connectors. This lets {@code BaseAttributeDefinition} derive the ConnId type from
 * {@code ScimAttributeMapping.connIdType()} instead of silently falling back to the
 * {@code String.class} default - previously a boolean-typed SCIM attribute like {@code active}
 * was always exposed to midPoint as a String, no matter what {@code scim { type "..." } } declared.
 */
public class ScimConnIdTypeInferenceTest {

    @Test
    public void booleanScimTypeIsInferredAsConnIdBooleanWithoutExplicitConnIdType() {
        var schema = new RestSchemaBuilderImpl(AbstractGroovyRestConnector.class, null);
        var loader = new GroovySchemaLoader(new GroovyContext(), schema);
        loader.load("""
                objectClass("User") {
                    attribute("active") {
                        scim {
                            path attribute("active")
                            type "boolean"
                        }
                    }
                }
                """);

        var attribute = schema.objectClass("User").attribute("active").build();

        assertEquals(attribute.connId().getType(), Boolean.class);
    }

    @Test
    public void stringScimTypeIsInferredAsConnIdStringWithoutExplicitConnIdType() {
        var schema = new RestSchemaBuilderImpl(AbstractGroovyRestConnector.class, null);
        var loader = new GroovySchemaLoader(new GroovyContext(), schema);
        loader.load("""
                objectClass("User") {
                    attribute("userName") {
                        scim {
                            path attribute("userName")
                            type "string"
                        }
                    }
                }
                """);

        var attribute = schema.objectClass("User").attribute("userName").build();

        assertEquals(attribute.connId().getType(), String.class);
    }

    @Test
    public void explicitConnIdTypeStillOverridesScimType() {
        var schema = new RestSchemaBuilderImpl(AbstractGroovyRestConnector.class, null);
        var loader = new GroovySchemaLoader(new GroovyContext(), schema);
        loader.load("""
                objectClass("User") {
                    attribute("active") {
                        connId { type String.class }
                        scim {
                            path attribute("active")
                            type "boolean"
                        }
                    }
                }
                """);

        var attribute = schema.objectClass("User").attribute("active").build();

        assertEquals(attribute.connId().getType(), String.class);
    }
}
