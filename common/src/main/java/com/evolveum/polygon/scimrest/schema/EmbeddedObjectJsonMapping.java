/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */

package com.evolveum.polygon.scimrest.schema;

import com.evolveum.polygon.scimrest.ContextLookup;
import com.evolveum.polygon.scimrest.ValueMapping;
import com.evolveum.polygon.scimrest.groovy.ConnectorContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.EmbeddedObject;

import java.nio.channels.UnsupportedAddressTypeException;
import java.util.HashSet;
import java.util.Set;

public class EmbeddedObjectJsonMapping implements ValueMapping<EmbeddedObject, JsonNode> {

    private final ContextLookup lookup;
    private final String name;
    private MappedObjectClass schema;


    public EmbeddedObjectJsonMapping(ContextLookup contextLookup, String objectClassName) {
        this.lookup = contextLookup;
        this.name = objectClassName;
    }

    @Override
    public Class<EmbeddedObject> connIdType() {
        return EmbeddedObject.class;
    }

    @Override
    public Class<? extends JsonNode> primaryWireType() {
        return ObjectNode.class;
    }

    @Override
    public JsonNode toWireValue(EmbeddedObject value) throws IllegalArgumentException {
        throw new UnsupportedAddressTypeException();
    }

    MappedObjectClass schema() {
        if (this.schema == null) {
            this.schema = lookup.get(ConnectorContext.class).schema().objectClass(name);
        }
        return this.schema;
    }

    @Override
    public EmbeddedObject toConnIdValue(JsonNode value) throws IllegalArgumentException {
        MappedObjectClass objectClass = schema();
        if (value instanceof ObjectNode remoteObj) {
            if (remoteObj.isEmpty()) {
                return null;
            }
            Set<Attribute> attributes = new HashSet<>();
            for (var attributeDef : objectClass.attributes()) {
                var valueMapping = attributeDef.mapping(JsonAttributeMapping.class);
                if (valueMapping != null) {
                    Object connIdValues = valueMapping.valuesFromObject(remoteObj);
                    if (connIdValues != null) {
                        attributes.add(attributeDef.attributeOf(connIdValues));
                    }
                }
            }
            return new EmbeddedObject(objectClass.objectClass(), attributes);
        }
        return null;
    }
}
