/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.schema;

import com.evolveum.polygon.conndev.api.AttributePathDeclaration;
import com.evolveum.polygon.conndev.json.JsonAttributeMapping;
import com.evolveum.polygon.conndev.spi.ValueMapping;
import tools.jackson.databind.JsonNode;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;

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
}
