/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 */
package com.evolveum.polygon.scimrest.unit.groovy;

import com.evolveum.polygon.conndev.json.JsonAttributeMapping;
import com.evolveum.polygon.scimrest.groovy.connector.AbstractGroovyRestConnector;
import com.evolveum.polygon.scimrest.groovy.connector.RestConnectorContext;
import com.evolveum.polygon.scimrest.groovy.impl.ReadOnlyConfiguration;
import com.evolveum.polygon.scimrest.groovy.schema.SchemaDefinitionLoader;
import com.evolveum.polygon.scimrest.schema.RestSchemaBuilderImpl;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObjectReference;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.testng.Assert.*;

public class UidReferenceLinkTest {
    private static final String YAML = """
            objectClasses:
              Group:
                references:
                  members:
                    objectClass: User
                    multiValued: true
                    json:
                      path: $._links.members
                      referenceBy:
                        uidFrom: href
                        template: /api/v3/users/{uid}
                        nameFrom: title
            """;
    private static final String GROOVY = """
            objectClass('Group') {
                reference('members') {
                    objectClass 'User'
                    multiValued true
                    json {
                        path attribute('_links').child('members')
                        referenceBy {
                            uidFrom 'href'
                            template '/api/v3/users/{uid}'
                            nameFrom 'title'
                        }
                    }
                }
            }
            """;

    @DataProvider
    public Object[][] schemas() {
        return new Object[][] {{"Group.schema.yaml", YAML}, {"Group.schema.groovy", GROOVY}};
    }

    private static JsonAttributeMapping members(String file, String schema, String base) {
        var config = new ReadOnlyConfiguration();
        config.setBaseAddress(base);
        var builder = new RestSchemaBuilderImpl(AbstractGroovyRestConnector.class, new RestConnectorContext(config));
        var loader = new SchemaDefinitionLoader(config.groovyContext(), builder);
        loader.loadFromString(file, schema);
        return loader.build().objectClass("Group").attributeFromProtocolName("members").json();
    }

    @Test(dataProvider = "schemas")
    public void configuredInstanceReadsLinksAndWritesUidsInTheDeclaredFormat(String file, String schema) {
        var members = members(file, schema, "https://op.example.test/api/v3");
        var json = new ObjectMapper();
        var input = json.createObjectNode();
        var links = input.putObject("_links").putArray("members");
        links.addObject().putNull("href");
        links.addObject().put("href", "https://OP.EXAMPLE.TEST:443/api/v3/users/42").put("title", "Alice");
        links.addObject().put("href", "/api/v3/users/A%2FB%20C%2BD");
        var refs = members.valuesFromObject(input);
        assertEquals(refs.size(), 2);
        var ref = (ConnectorObjectReference) refs.get(0);
        assertFalse(ref.hasObject());
        assertEquals(AttributeUtil.getUidAttribute(ref.getValue().getAttributes()), new Uid("42"));
        assertEquals(AttributeUtil.getNameFromAttributes(ref.getValue().getAttributes()).getNameValue(), "Alice");
        var output = json.createObjectNode();
        members.toJsonNode(AttributeBuilder.build("members", refs), output);
        assertEquals(output.at("/_links/members/0/href").asString(), "/api/v3/users/42");

        assertEquals(output.at("/_links/members/1/href").asString(), "/api/v3/users/A%2FB%20C%2BD");
        assertFalse(output.at("/_links/members/0").has("title"));
        var second = (ConnectorObjectReference) refs.get(1);
        assertEquals(AttributeUtil.getUidAttribute(second.getValue().getAttributes()), new Uid("A/B C+D"));
        assertEquals(AttributeUtil.getNameFromAttributes(second.getValue().getAttributes()).getNameValue(), "A/B C+D");
    }

    @Test(dataProvider = "schemas")
    public void rejectsForeignOriginsAndWrongResourcePaths(String file, String schema) {
        var mapping = members(file, schema, "https://op.example.test/api/v3");
        var json = new ObjectMapper();
        for (var href : List.of("https://other.test/api/v3/users/42", "http://op.example.test/api/v3/users/42",
                "https://op.example.test:444/api/v3/users/42", "https://op.example.test/api/v3/groups/42",
                "//other.test/api/v3/users/42", "https://other.test@op.example.test/api/v3/users/42",
                "/api/v3/groups/42", "/api/v3/users/", "/api/v3/users/42/activity", "/api/v3/users/42?details")) {
            var link = json.createObjectNode().put("href", href);
            expectThrows(IllegalArgumentException.class, () -> mapping.singleValueFromAttribute(link));
        }
        var offline = members(file, schema, null);
        var local = json.createObjectNode().put("href", "/api/v3/users/42");
        assertTrue(offline.singleValueFromAttribute(local) instanceof ConnectorObjectReference);
        var absolute = json.createObjectNode().put("href", "https://op.example.test/api/v3/users/42");
        expectThrows(IllegalArgumentException.class, () -> offline.singleValueFromAttribute(absolute));
    }

    @Test
    public void malformedLinkTemplateFailsAtSchemaBuild() {
        expectThrows(IllegalArgumentException.class,
                () -> members("Group.schema.yaml", YAML.replace("{uid}", "42"), null));
    }

    @Test(dataProvider = "schemas")
    public void dotSegmentsCannotIdentifyResources(String file, String schema) {
        var mapping = members(file, schema, "https://op.example.test/api/v3");
        var json = new ObjectMapper();
        for (String segment : List.of(".", "..", "%2E", ".%2e", "%2e%2E")) {
            var link = json.createObjectNode().put("href", "/api/v3/users/" + segment);
            expectThrows(IllegalArgumentException.class, () -> mapping.singleValueFromAttribute(link));
        }
        for (String uid : List.of(".", "..")) {
            var user = new ConnectorObjectBuilder().setObjectClass(new ObjectClass("User")).setUid(uid);
            var attribute = AttributeBuilder.build("members", new ConnectorObjectReference(user.buildIdentification()));
            expectThrows(IllegalArgumentException.class, () -> mapping.toJsonNode(attribute, json.createObjectNode()));
        }

        var input = json.createObjectNode();
        input.putObject("_links").putArray("members").addObject().put("href", "/api/v3/users/alice.smith");
        var output = json.createObjectNode();
        mapping.toJsonNode(AttributeBuilder.build("members", mapping.valuesFromObject(input)), output);
        assertEquals(output, input);
    }

    @Test
    public void plainUidsOnTheRestBuilderUseNoInstanceAddress() {
        var mapping = members("Group.schema.yaml", """
                objectClasses:
                  Group:
                    references:
                      members:
                        objectClass: User
                        multiValued: true
                        json: {referenceBy: uid}
                """, null);
        var json = new ObjectMapper();
        var input = json.createObjectNode();
        input.putArray("members").add("user-id");
        var refs = mapping.valuesFromObject(input);
        var ref = (ConnectorObjectReference) refs.get(0);
        assertEquals(ref.getValue().getObjectClass().getObjectClassValue(), "User");
        assertEquals(AttributeUtil.getUidAttribute(ref.getValue().getAttributes()), new Uid("user-id"));
        var output = json.createObjectNode();
        mapping.toJsonNode(AttributeBuilder.build("members", refs), output);
        assertEquals(output, input);
    }
}
