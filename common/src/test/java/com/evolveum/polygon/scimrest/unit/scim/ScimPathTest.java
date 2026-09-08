/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.unit.scim;

import com.evolveum.polygon.conndev.api.AttributePath;
import com.evolveum.polygon.conndev.api.AttributePathFormatException;
import com.evolveum.polygon.conndev.api.ParsingException;
import com.evolveum.polygon.conndev.json.JsonAttributeMapping;
import com.evolveum.polygon.scimrest.schema.ScimPathFormat;
import org.testng.annotations.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testng.Assert.assertThrows;

public class ScimPathTest {

    private static final String ENTERPRISE_URI = "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User";

    private static final ScimPathFormat INSTANCE = ScimPathFormat.INSTANCE;
    // ==================== Parsing ====================

    @Test
    public void testParse_singleAttribute() {
        assertThat(INSTANCE.parse("userName")).isEqualTo(AttributePath.of("userName"));
    }

    @Test
    public void testParse_nestedAttributes() {
        assertThat(INSTANCE.parse("name.givenName")).isEqualTo(AttributePath.of("name", "givenName"));
    }

    @Test
    public void testParse_attributeNameCharacters() {
        assertThat(INSTANCE.parse("my-attr_2.x$y"))
                .isEqualTo(AttributePath.of("my-attr_2", "x$y"));
    }

    @Test
    public void testParse_extensionUriPrefix() {
        var path = INSTANCE.parse(ENTERPRISE_URI + ":employeeNumber");
        assertThat(path).isEqualTo(AttributePath.of(
                new AttributePath.Extension(ENTERPRISE_URI),
                new AttributePath.Attribute("employeeNumber")));
    }

    @Test
    public void testParse_extensionUriWithSubAttributes() {
        var path = INSTANCE.parse("urn:ietf:params:scim:schemas:core:2.0:User:name.givenName");
        assertThat(path).isEqualTo(AttributePath.of(
                new AttributePath.Extension("urn:ietf:params:scim:schemas:core:2.0:User"),
                new AttributePath.Attribute("name"),
                new AttributePath.Attribute("givenName")));
    }

    @Test
    public void testParse_indexFilter() {
        assertThat(INSTANCE.parse("emails[0].value"))
                .isEqualTo(new AttributePath(List.of(
                        new AttributePath.Attribute("emails"),
                        new AttributePath.IndexFilter(0),
                        new AttributePath.Attribute("value"))));
    }

    @Test
    public void testParse_valueFilterWithSubAttribute() {
        var path = INSTANCE.parse("emails[type eq \"work\"].value");
        assertThat(path).isEqualTo(new AttributePath(List.of(
                new AttributePath.Attribute("emails"),
                new AttributePath.SimpleValueFilter(Map.of("type", "work")),
                new AttributePath.Attribute("value"))));
    }

    @Test
    public void testParse_valueFilterOnly() {
        var path = INSTANCE.parse("addresses[type eq \"work\"]");
        assertThat(path).isEqualTo(new AttributePath(List.of(
                new AttributePath.Attribute("addresses"),
                new AttributePath.SimpleValueFilter(Map.of("type", "work")))));
    }

    @Test
    public void testParse_multiConditionFilter() {
        var path = INSTANCE.parse("costs[price eq 100 and currency eq \"USD\"]");
        assertThat(path).isEqualTo(new AttributePath(List.of(
                new AttributePath.Attribute("costs"),
                new AttributePath.SimpleValueFilter(Map.of("price", 100, "currency", "USD")))));
    }

    @Test
    public void testParse_filterValueTypes() {
        var path = INSTANCE.parse("items[active eq true and deleted eq null and id eq 42.5]");
        var values = new LinkedHashMap<String, Object>();
        values.put("active", Boolean.TRUE);
        values.put("deleted", null);
        values.put("id", 42.5);
        assertThat(path).isEqualTo(new AttributePath(List.of(
                new AttributePath.Attribute("items"),
                new AttributePath.SimpleValueFilter(values))));
    }

    @Test
    public void testParse_longFilterValue() {
        var path = INSTANCE.parse("items[id eq 3000000000]");
        assertThat(path).isEqualTo(new AttributePath(List.of(
                new AttributePath.Attribute("items"),
                new AttributePath.SimpleValueFilter(Map.of("id", 3000000000L)))));
    }

    @Test
    public void testParse_escapedStringInFilter() {
        var path = INSTANCE.parse("names[name eq \"a\\nb\"]");
        assertThat(path).isEqualTo(new AttributePath(List.of(
                new AttributePath.Attribute("names"),
                new AttributePath.SimpleValueFilter(Map.of("name", "a\nb")))));
    }

    @Test
    public void testParse_invalidInputs() {
        for (var input : new String[]{
                null,
                "",
                "   ",
                ".givenName",
                "1name",
                "name.",
                "name..givenName",
                "[0]",
                "name[0x]",
                "name[x gt 1]",
                "name[x ne 1]",
                "name[x eq 1 or y eq 2]",
                "name[pr]",
                "name[eq 1]",
                "items[id eq 1 and id eq 2]",
                "name[type eq]",
                "urn:",
                ":name",
                "name.givenName extra",
                "items[type eq \"\\uZZZZ\"]",
                "items[type eq \"\\u12G4\"]",
        }) {
            assertThrows("Expected ParsingException for: " + input, ParsingException.class,
                    () -> INSTANCE.parse(input));
        }
    }

    // ==================== Serialization ====================

    @Test
    public void testSerialize_flat() {
        assertThat(INSTANCE.serialize(AttributePath.of("name", "givenName")))
                .isEqualTo("name.givenName");
    }

    @Test
    public void testSerialize_extensionPrefix() {
        var path = AttributePath.of(
                new AttributePath.Extension(ENTERPRISE_URI),
                new AttributePath.Attribute("employeeNumber"));
        assertThat(INSTANCE.serialize(path)).isEqualTo(ENTERPRISE_URI + ":employeeNumber");
    }

    @Test
    public void testSerialize_indexFilter() {
        assertThat(INSTANCE.serialize(AttributePath.of("emails").firstValue().child("value")))
                .isEqualTo("emails[0].value");
    }

    @Test
    public void testSerialize_valueFilter() {
        var path = AttributePath.of("emails").valueFilter("type", "work").child("value");
        assertThat(INSTANCE.serialize(path)).isEqualTo("emails[type eq \"work\"].value");
    }

    @Test
    public void testSerialize_valueFilterValueTypes() {
        assertThat(INSTANCE.serialize(AttributePath.of("items").valueFilter("id", 42)))
                .isEqualTo("items[id eq 42]");
        assertThat(INSTANCE.serialize(AttributePath.of("items").valueFilter("active", true)))
                .isEqualTo("items[active eq true]");
        assertThat(INSTANCE.serialize(AttributePath.of("items").valueFilter("rate", 1.5)))
                .isEqualTo("items[rate eq 1.5]");
    }

    @Test
    public void testSerialize_valueFilterNullValue() {
        var values = new LinkedHashMap<String, Object>();
        values.put("type", null);
        var path = new AttributePath(List.of(
                new AttributePath.Attribute("items"),
                new AttributePath.SimpleValueFilter(values)));
        assertThat(INSTANCE.serialize(path)).isEqualTo("items[type eq null]");
    }

    @Test
    public void testSerialize_multiConditionFilter() {
        var values = new LinkedHashMap<String, Object>();
        values.put("price", 100);
        values.put("currency", "USD");
        var path = new AttributePath(List.of(
                new AttributePath.Attribute("costs"),
                new AttributePath.SimpleValueFilter(values)));
        assertThat(INSTANCE.serialize(path)).isEqualTo("costs[price eq 100 and currency eq \"USD\"]");
    }

    @Test
    public void testSerialize_stringEscaping() {
        var values = new LinkedHashMap<String, Object>();
        values.put("note", "a\"b\\c");
        var path = new AttributePath(List.of(
                new AttributePath.Attribute("items"),
                new AttributePath.SimpleValueFilter(values)));
        assertThat(INSTANCE.serialize(path)).isEqualTo("items[note eq \"a\\\"b\\\\c\"]");
    }

    // ==================== Serialization errors ====================

    @Test
    public void testSerialize_invalidPaths() {
        assertThrows("Expected exception for null path", AttributePathFormatException.class,
                () -> INSTANCE.serialize(null));
        assertThrows("Expected exception for empty path", AttributePathFormatException.class,
                () -> INSTANCE.serialize(new AttributePath(List.of())));
        assertThrows("Expected exception for extension not first", AttributePathFormatException.class,
                () -> INSTANCE.serialize(new AttributePath(List.of(
                        new AttributePath.Attribute("a"),
                        new AttributePath.Extension("urn:x"),
                        new AttributePath.Attribute("b")))));
        assertThrows("Expected exception for negative index", AttributePathFormatException.class,
                () -> INSTANCE.serialize(
                        new AttributePath(List.of(new AttributePath.Attribute("a"), new AttributePath.IndexFilter(-1)))));
        assertThrows("Expected exception for invalid attribute name", AttributePathFormatException.class,
                () -> INSTANCE.serialize(AttributePath.of("a b")));
        assertThrows("Expected exception for empty value filter", AttributePathFormatException.class,
                () -> INSTANCE.serialize(new AttributePath(List.of(
                        new AttributePath.Attribute("items"),
                        new AttributePath.SimpleValueFilter(Map.of())))));
    }

    // ==================== Round-trip ====================

    @Test
    public void testRoundTrip() {
        var values = new LinkedHashMap<String, Object>();
        values.put("price", 100);
        values.put("currency", "USD");
        var paths = List.of(
                AttributePath.of("userName"),
                AttributePath.of("name", "givenName"),
                AttributePath.of(
                        new AttributePath.Extension(ENTERPRISE_URI),
                        new AttributePath.Attribute("employeeNumber")),
                AttributePath.of(
                        new AttributePath.Extension("urn:ietf:params:scim:schemas:core:2.0:User"),
                        new AttributePath.Attribute("name"),
                        new AttributePath.Attribute("givenName")),
                AttributePath.of("emails").firstValue().child("value"),
                AttributePath.of("emails").valueFilter("type", "work").child("value"),
                new AttributePath(List.of(
                        new AttributePath.Attribute("costs"),
                        new AttributePath.SimpleValueFilter(values))));
        for (var path : paths) {
            var serialized = INSTANCE.serialize(path);
            assertThat(INSTANCE.parse(serialized)).isEqualTo(path);
            assertThat(INSTANCE.serialize(INSTANCE.parse(serialized))).isEqualTo(serialized);
        }
    }

    // ==================== Resolution ====================

    @Test
    public void testParsedPathResolves() {
        var mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        var emails = root.putArray("emails");
        var work = emails.addObject();
        work.put("type", "work");
        work.put("value", "jdoe@example.com");
        var home = emails.addObject();
        home.put("type", "home");
        home.put("value", "jdoe@home.example");

        var path = INSTANCE.parse("emails[type eq \"work\"].value");
        var resolved = path.resolve(root, JsonAttributeMapping.NULLABLE_PATH_RESOLVER);
        assertThat(resolved).isNotNull();
        assertThat(resolved.asText()).isEqualTo("jdoe@example.com");
    }
}
