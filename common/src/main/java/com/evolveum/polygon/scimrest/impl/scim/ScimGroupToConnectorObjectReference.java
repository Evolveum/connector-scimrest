/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.scim;

import com.evolveum.polygon.scimrest.JsonValueMapping;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObjectReference;
import org.identityconnectors.framework.common.objects.ObjectClass;

import java.util.Set;

public class ScimGroupToConnectorObjectReference implements JsonValueMapping {

    private final ObjectClass referencedObjectClass;

    public ScimGroupToConnectorObjectReference(ObjectClass targetObjectClass) {
        this.referencedObjectClass = targetObjectClass;
    }

    @Override
    public Class<ConnectorObjectReference> connIdType() {
        return  ConnectorObjectReference.class;
    }

    @Override
    public Class<? extends JsonNode> primaryWireType() {
        return ObjectNode.class;
    }

    @Override
    public Set<Class<? extends JsonNode>> supportedWireTypes() {
        return Set.of(ObjectNode.class);
    }

    @Override
    public JsonNode toWireValue(Object value) throws IllegalArgumentException {
        return null;
    }

    @Override
    public ConnectorObjectReference toConnIdValue(JsonNode value) throws IllegalArgumentException {
        if (value instanceof ObjectNode remote) {
            var builder = new ConnectorObjectBuilder();
            builder.setObjectClass(referencedObjectClass);
            builder.setUid(remote.get("value").asText());
            builder.setName(remote.get("display").asText());
            return new ConnectorObjectReference(builder.build());
        }
        throw new IllegalArgumentException();
    }
}
