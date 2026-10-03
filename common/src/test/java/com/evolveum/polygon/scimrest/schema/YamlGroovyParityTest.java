/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.schema;

import com.evolveum.polygon.conndev.groovy.GroovyContext;
import com.evolveum.polygon.conndev.json.JsonAttributeMapping;
import com.evolveum.polygon.scimrest.groovy.connector.AbstractGroovyRestConnector;
import com.evolveum.polygon.scimrest.groovy.schema.SchemaDefinitionLoader;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectReference;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.Uid;
import org.testng.annotations.Test;
import tools.jackson.databind.node.JsonNodeFactory;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Groovy/YAML parity for the MID #12495 fix: the declarative {@code json: implementation:}
 * block must produce a functional {@link JsonAttributeMapping} equivalent to the Groovy DSL's
 * {@code json { implementation { deserialize { ... } } }}. Both sides are the same
 * Membership/project reference, shipped as sibling test resources and loaded the way a
 * connector bundle loads them ({@link SchemaDefinitionLoader#loadFromResource}).
 */
public class YamlGroovyParityTest {

    private static final Uid EXPECTED_UID = new Uid("123");
    private static final Name EXPECTED_NAME = new Name("Proj");

    @Test
    public void groovyAndYamlImplementationsProduceEquivalentMappings() {
        var groovyMapping = loadMapping("/parity/Membership.native.schema.groovy");
        var yamlMapping = loadMapping("/parity/Membership.native.schema.yaml");

        var sample = JsonNodeFactory.instance.objectNode()
                .set("_links", JsonNodeFactory.instance.objectNode()
                        .set("project", JsonNodeFactory.instance.objectNode()
                                .set("href", JsonNodeFactory.instance.textNode("https://op.example.org/api/v3/projects/123"))
                                .set("title", JsonNodeFactory.instance.textNode("Proj"))));

        var groovyConnId = groovyMapping.singleValueFromAttribute(groovyMapping.attributeFromObject(sample));
        var yamlConnId = yamlMapping.singleValueFromAttribute(yamlMapping.attributeFromObject(sample));

        assertTrue(groovyConnId instanceof ConnectorObjectReference,
                "Groovy side produced " + groovyConnId);
        assertTrue(yamlConnId instanceof ConnectorObjectReference,
                "YAML side produced " + yamlConnId);

        var groovyRef = (ConnectorObject) ((ConnectorObjectReference) groovyConnId).getValue();
        var yamlRef = (ConnectorObject) ((ConnectorObjectReference) yamlConnId).getValue();
        assertEquals(groovyRef.getUid(), EXPECTED_UID, "Groovy side uid");
        assertEquals(groovyRef.getName(), EXPECTED_NAME, "Groovy side name");
        assertEquals(yamlRef.getUid(), EXPECTED_UID, "YAML side uid");
        assertEquals(yamlRef.getName(), EXPECTED_NAME, "YAML side name");
    }

    private static JsonAttributeMapping loadMapping(String resource) {
        var builder = new RestSchemaBuilderImpl(AbstractGroovyRestConnector.class, null);
        var loader = new SchemaDefinitionLoader(new GroovyContext(), builder);
        loader.loadFromResource(resource);
        return loader.build().objectClass("Membership").attributeFromProtocolName("project").json();
    }
}
