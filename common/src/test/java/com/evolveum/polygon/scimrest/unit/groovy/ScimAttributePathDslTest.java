/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.unit.groovy;

import com.evolveum.polygon.scimrest.groovy.connector.AbstractGroovyRestConnector;
import com.evolveum.polygon.conndev.groovy.GroovyContext;
import com.evolveum.polygon.conndev.groovy.GroovySchemaLoader;
import com.evolveum.polygon.conndev.api.AttributePath;
import com.evolveum.polygon.conndev.api.ParsingException;
import com.evolveum.polygon.scimrest.schema.RestAttributeDefinition;
import com.evolveum.polygon.scimrest.schema.RestSchemaBuilderImpl;
import com.evolveum.polygon.scimrest.schema.ScimPathFormat;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * {@code scim { path("...") } } parses SCIM attribute paths (nested sub-attributes, value
 * filters, schema extension URIs) into the shared {@code AttributePath} model using the
 * SCIM path grammar.
 */
public class ScimAttributePathDslTest {

    private final RestSchemaBuilderImpl schema = new RestSchemaBuilderImpl(AbstractGroovyRestConnector.class, null);
    private final GroovySchemaLoader loader = new GroovySchemaLoader(new GroovyContext(), schema);

    private RestAttributeDefinition attribute(String name, String path) {
        loader.load("""
                objectClass("User") {
                    attribute("%s") {
                        scim {
                            path('%s')
                            type "string"
                        }
                    }
                }
                """.formatted(name, path));
        return schema.objectClass("User").attribute(name).build();
    }

    @Test
    public void flatPathIsAccepted() {
        var attribute = attribute("userName", "userName");

        assertEquals(ScimPathFormat.INSTANCE.serialize(attribute.scim().path()), "userName");
    }

    @Test
    public void nestedPathIsAccepted() {
        var attribute = attribute("givenName", "name.givenName");

        assertEquals(ScimPathFormat.INSTANCE.serialize(attribute.scim().path()), "name.givenName");
    }

    @Test
    public void valueFilterPathIsAccepted() {
        var attribute = attribute("workEmail", "emails[type eq \"work\"].value");

        assertEquals(ScimPathFormat.INSTANCE.serialize(attribute.scim().path()), "emails[type eq \"work\"].value");
        assertTrue(attribute.scim().path().components().stream()
                .anyMatch(component -> component instanceof AttributePath.FilterComponent));
    }

    @Test
    public void extensionUriPathIsAccepted() {
        var uri = "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User";
        var attribute = attribute("employeeNumber", uri + ":employeeNumber");

        assertEquals(ScimPathFormat.INSTANCE.serialize(attribute.scim().path()), uri + ":employeeNumber");
    }

    @Test
    public void invalidPathIsRejected() {
        assertThrows(ParsingException.class, () -> attribute("bad", "1name"));
    }
}
