/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.schema;

import com.evolveum.polygon.conndev.api.AttributePath;
import com.evolveum.polygon.conndev.api.AttributePathDeclaration;
import com.evolveum.polygon.conndev.json.JsonAttributeMapping;
import com.evolveum.polygon.conndev.spi.ValueMapping;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.objects.Attribute;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ScimAttributeMapping extends JsonAttributeMapping {

    public ScimAttributeMapping(AttributePathDeclaration<?,?> path, ValueMapping<Object, JsonNode> mapping) {
        super(path, mapping);
        if (mapping == null) {
            // Without a value mapping every read/write of this attribute would NPE at runtime.
            throw new ConfigurationException(
                    "Value mapping is null for SCIM attribute mapping " + (path == null ? "<unknown>" : path));
        }
    }

    /**
     * The path components this mapping actually navigates in the wire JSON: for a path qualified
     * with a SCIM schema extension URI, the leading {@link AttributePath.Extension} component is
     * dropped — per RFC 7643 the extension's attributes are merged into the resource's top level,
     * so on the wire they live in the same JSON object as the primary attributes, not under a key
     * named after the schema URI. Returns {@code null} when no path is configured.
     */
    private AttributePath effectivePath() {
        var path = path();
        if (path == null || path.components().isEmpty()) {
            return null;
        }
        var components = path.components();
        if (components.getFirst() instanceof AttributePath.Extension) {
            var stripped = components.subList(1, components.size());
            return stripped.isEmpty() ? null : new AttributePath(List.copyOf(stripped));
        }
        return path;
    }

    @Override
    public JsonNode attributeFromObject(ObjectNode object) {
        var effective = effectivePath();
        if (effective == null) {
            return null;
        }
        return effective.resolve(object, NULLABLE_PATH_RESOLVER);
    }

    /**
     * Serializes this attribute's SCIM path to SCIM attribute-path notation (RFC 7643/7644),
     * e.g. {@code userName}, {@code name.givenName},
     * {@code urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:employeeNumber}.
     *
     * @throws ConfigurationException if no SCIM path is configured for the attribute
     */
    public String scimPath() {
        var path = path();
        if (path == null) {
            throw new ConfigurationException("No SCIM path configured for attribute mapping");
        }
        return ScimPathFormat.INSTANCE.serialize(path);
    }

    /**
     * Converts ConnId attribute values to their SCIM wire (JSON) representation.
     *
     * @param values the ConnId values to convert
     * @return the corresponding JSON nodes, in the same order
     */
    public List<JsonNode> wireValues(Collection<?> values) {
        var result = new ArrayList<JsonNode>(values.size());
        for (var value : values) {
            result.add(valueMapping.toWireValue(value));
        }
        return result;
    }

    /**
     * Converts ConnId attribute values to their SCIM wire (JSON) representation.
     *
     * <p>All navigation happens on the wire shape (see {@link #effectivePath()}: a leading SCIM
     * extension component is not a JSON key — the extension's attributes live at the resource's
     * top level). For a path that selects a sub-attribute of an entry of a multi-valued attribute
     * by a value filter (e.g. {@code emails[type eq "work"].value}), the value is written into the
     * matching entry of the array — creating the entry (self-describing via the filter keys) when
     * it does not exist yet — instead of being written as a nested object as the base
     * implementation would. Other paths containing a value filter (e.g. a terminal
     * {@code emails[type eq "work"]}) fall back to the base implementation.
     */
    @Override
    public void toJsonNode(Attribute attribute, ObjectNode parent) {
        var effective = effectivePath();
        if (effective == null) {
            return;
        }
        var filterIndex = indexOfFirstValueFilter(effective);
        if (filterIndex >= 0 && isFilterEntryFieldShape(effective, filterIndex)) {
            var values = attribute.getValue().stream()
                    .map(valueMapping::toWireValue)
                    .toList();
            if (!values.isEmpty()) {
                writeFiltered(parent, effective, filterIndex, values);
            }
            return;
        }
        if (path().components().getFirst() instanceof AttributePath.Extension) {
            // A structural path the base implementation cannot navigate (its traversal stops at
            // the extension component) — navigate the top-level shape directly.
            writeStructural(parent, effective, attribute);
            return;
        }
        super.toJsonNode(attribute, parent);
    }

    private int indexOfFirstValueFilter(AttributePath path) {
        if (path == null) {
            return -1;
        }
        var components = path.components();
        for (int i = 0; i < components.size(); i++) {
            if (components.get(i) instanceof AttributePath.SimpleValueFilter) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Checks whether the path has the shape required by {@link #writeFiltered(ObjectNode, int, List)}:
     * a value filter bracketed by attribute components, i.e.
     * {@code <attribute>* <array>[<filter>] <field>} (e.g. {@code emails[type eq "work"].value}).
     * A filter at any other position (e.g. a terminal {@code emails[type eq "work"]}) is not
     * supported by the filtered write.
     */
    private static boolean isFilterEntryFieldShape(AttributePath path, int filterIndex) {
        var components = path.components();
        if (filterIndex <= 0 || filterIndex + 1 >= components.size()) {
            return false;
        }
        for (int i = 0; i < filterIndex; i++) {
            if (!(components.get(i) instanceof AttributePath.Attribute)) {
                return false;
            }
        }
        return components.get(filterIndex + 1) instanceof AttributePath.Attribute;
    }

    /**
     * Writes the given wire values into the entry of the multi-valued attribute selected by the
     * value filter at {@code filterIndex} in the given (wire-shape, see {@link #effectivePath()})
     * path.
     */
    private void writeFiltered(ObjectNode root, AttributePath path, int filterIndex, List<JsonNode> values) {
        var components = path.components();
        var filter = (AttributePath.SimpleValueFilter) components.get(filterIndex);
        var arrayName = ((AttributePath.Attribute) components.get(filterIndex - 1)).name();
        var fieldName = ((AttributePath.Attribute) components.get(filterIndex + 1)).name();

        // Navigate to the object that directly contains the multi-valued (array) attribute.
        var current = root;
        for (int i = 0; i < filterIndex - 1; i++) {
            if (!(components.get(i) instanceof AttributePath.Attribute segment)) {
                return;
            }
            if (!current.has(segment.name()) || !current.get(segment.name()).isObject()) {
                current.putObject(segment.name());
            }
            current = current.withObject(segment.name());
        }

        var existing = current.get(arrayName);
        var array = existing != null && existing.isArray() ? existing.asArray() : current.putArray(arrayName);

        var entry = findEntry(array, filter);
        if (entry == null) {
            entry = array.addObject();
            populateFilterKeys(entry, filter);
        }

        entry.set(fieldName, values.size() == 1 ? values.getFirst() : root.arrayNode().addAll(values));
    }

    /** Stamps the discriminator keys of the filter onto a freshly created array entry. */
    private static void populateFilterKeys(ObjectNode entry, AttributePath.SimpleValueFilter filter) {
        for (var keyValue : filter.keyValues().entrySet()) {
            entry.put(keyValue.getKey(), keyValue.getValue() instanceof String s ? s : String.valueOf(keyValue.getValue()));
        }
    }

    /**
     * Writes the attribute's wire values along a structural path — the same traversal as the base
     * implementation, but against the given (wire-shape, see {@link #effectivePath()}) path, which
     * the base cannot navigate when it carries a leading {@link AttributePath.Extension} component.
     */
    private void writeStructural(ObjectNode parent, AttributePath path, Attribute attribute) {
        var values = attribute.getValue().stream()
                .map(valueMapping::toWireValue)
                .toList();
        if (values.isEmpty()) {
            return;
        }
        var components = path.withoutFilters().components();
        if (components.isEmpty()) {
            return;
        }
        ObjectNode current = parent;
        for (int i = 0; i < components.size() - 1; i++) {
            if (!(components.get(i) instanceof AttributePath.Attribute attr)) {
                return;
            }
            if (!current.has(attr.name()) || !current.get(attr.name()).isObject()) {
                current.putObject(attr.name());
            }
            current = current.withObject(attr.name());
        }
        var last = components.getLast();
        if (last instanceof AttributePath.Attribute attr) {
            current.set(attr.name(), values.size() == 1 ? values.getFirst() : parent.arrayNode().addAll(values));
        }
    }

    private ObjectNode findEntry(ArrayNode array, AttributePath.SimpleValueFilter filter) {
        for (var node : array) {
            if (node.isObject() && matchesEntry(node.asObject(), filter)) {
                return node.asObject();
            }
        }
        return null;
    }

    private static boolean matchesEntry(ObjectNode entry, AttributePath.SimpleValueFilter filter) {
        for (var keyValue : filter.keyValues().entrySet()) {
            var actual = entry.get(keyValue.getKey());
            if (actual == null || !matchesValue(actual, keyValue.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesValue(JsonNode actual, Object expected) {
        if (expected == null) {
            return actual.isNull();
        }
        return switch (expected) {
            case String s -> actual.isTextual() && s.equals(actual.asString());
            case Boolean b -> actual.isBoolean() && b.equals(actual.asBoolean());
            case Number n -> actual.isNumber() && n.doubleValue() == actual.asDouble();
            default -> false;
        };
    }
}
