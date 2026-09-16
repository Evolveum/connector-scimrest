/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.scim;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.groovy.ConnectorContext;
import com.evolveum.polygon.conndev.schema.BaseAttributeDefinition;
import com.evolveum.polygon.conndev.schema.BaseObjectClassDefinition;
import com.evolveum.polygon.conndev.spi.ValueMapping;
import com.evolveum.polygon.scimrest.schema.RestAttributeDefinition;
import com.evolveum.polygon.scimrest.schema.ScimAttributeMapping;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.EmbeddedObject;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.HashSet;
import java.util.Set;

/**
 * Value mapping that deserializes a SCIM complex attribute JSON object into an
 * {@link EmbeddedObject}, iterating the sub-attributes of the referenced embedded object class
 * and using their {@link ScimAttributeMapping} instances to resolve and convert each value.
 *
 * <p>Embedded class names follow the convention {@code ParentClassName__AttributeName},
 * e.g. {@code User__emails} for the {@code emails} attribute on the {@code User} object class.</p>
 */
public class ScimEmbeddedObjectValueMapping implements ValueMapping<EmbeddedObject, JsonNode> {

    private final ContextLookup lookup;
    private final String embeddedClassName;
    private BaseObjectClassDefinition<BaseAttributeDefinition> schema;

    public ScimEmbeddedObjectValueMapping(ContextLookup contextLookup, String objectClassName) {
        this.lookup = contextLookup;
        this.embeddedClassName = objectClassName;
    }

    @Override
    public Class<EmbeddedObject> connIdType() {
        return EmbeddedObject.class;
    }

    @Override
    public Class<? extends JsonNode> primaryWireType() {
        return ObjectNode.class;
    }

    BaseObjectClassDefinition<BaseAttributeDefinition> schema() {
        if (this.schema == null) {
            this.schema = lookup.get(ConnectorContext.class).schema().objectClass(embeddedClassName);
        }
        return this.schema;
    }

    @Override
    public JsonNode toWireValue(EmbeddedObject value) throws IllegalArgumentException {
        // An unsupported capability of the connector — a configuration-level limitation, not a
        // runtime error midPoint could retry.
        throw new ConfigurationException(
                "Writing to the embedded attribute class '" + embeddedClassName + "' is not supported yet");
    }

    @Override
    public EmbeddedObject toConnIdValue(JsonNode value) throws IllegalArgumentException {
        if (!(value instanceof ObjectNode objectNode)) {
            throw new ConnectorException(
                    "Expected a JSON object for embedded attribute '" + embeddedClassName +
                            "', got: " + (value == null ? "null" : value.getClass().getSimpleName()));
        }

        if (objectNode.isEmpty()) {
            return null;
        }

        var objectClass = schema();
        Set<Attribute> attributes = new HashSet<>();

        for (var attr : objectClass.attributes()) {
            var scim = ((RestAttributeDefinition) attr).scim();
            if (scim == null) {
                continue;
            }
            var connIdValues = scim.valuesFromObject(objectNode);
            if (connIdValues != null) {
                var attribute = attr.attributeOf(connIdValues.size() == 1 ? connIdValues.getFirst() : connIdValues);
                attributes.add(attribute);
            }
        }

        return new EmbeddedObject(objectClass.objectClass(), attributes);
    }
}
