/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.schema;

import com.evolveum.polygon.conndev.api.AttributePath;
import com.evolveum.polygon.conndev.json.JsonAttributeMapping;
import com.evolveum.polygon.conndev.spi.ValueMapping;
import org.identityconnectors.framework.common.objects.Attribute;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

public class ScimAttributeMapping extends JsonAttributeMapping {

    public ScimAttributeMapping(AttributePath path, ValueMapping<Object, JsonNode> mapping) {
        super(path, mapping);
    }

    @Override
    public JsonNode attributeFromObject(ObjectNode object) {
        if (path != null) {
            return path.resolve(object);
        }
        return null;
    }

    /**
     * Write the attribute value to the SCIM JSON object following the full mapping path.
     * Unlike {@link JsonAttributeMapping#toJsonNode}, multi-component paths (e.g. the
     * {@code name.formatted} path of a flattened attribute) are supported: intermediate
     * object nodes are created as needed and the value is stored at the leaf.
     */
    @Override
    public void toJsonNode(Attribute attribute, ObjectNode parent) {
        if (path == null || path.onlyAttribute() != null) {
            super.toJsonNode(attribute, parent);
            return;
        }
        var values = attribute.getValue().stream().map(valueMapping::toWireValue).toList();
        if (values.isEmpty()) {
            return;
        }
        var components = path.withoutFilters().components();
        var node = parent;
        for (var i = 0; i < components.size() - 1; i++) {
            var component = components.get(i);
            if (!(component instanceof AttributePath.Attribute attr)) {
                throw new IllegalArgumentException("Unsupported path component '" + component + "' in " + path);
            }
            if (!(node.get(attr.name()) instanceof ObjectNode objectNode)) {
                node = node.putObject(attr.name());
            } else {
                node = objectNode;
            }
        }
        var leaf = (AttributePath.Attribute) components.get(components.size() - 1);
        node.set(leaf.name(), values.size() == 1 ? values.getFirst() : node.arrayNode().addAll(values));
    }

    public AttributePath path() {
        return path;
    }

}
