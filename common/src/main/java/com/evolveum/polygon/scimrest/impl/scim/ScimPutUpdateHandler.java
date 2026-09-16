/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.scim;

import com.evolveum.polygon.conndev.build.api.UpdateOperationBuilder.UpdateRequest;
import com.evolveum.polygon.scimrest.schema.RestAttributeDefinition;
import com.evolveum.polygon.scimrest.schema.RestObjectClassDefinition;
import com.evolveum.polygon.scimrest.impl.rest.HttpExceptionMapper;
import com.unboundid.scim2.common.GenericScimResource;
import com.unboundid.scim2.common.exceptions.ScimException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.util.List;
import java.util.Set;

/**
 * SCIM update via a full {@code PUT} (replacement): each delta is applied on top of the original
 * state and the resulting attributes are sent as a single {@code PUT} request.
 */
public class ScimPutUpdateHandler extends AbstractScimUpdateHandler {

    public ScimPutUpdateHandler(ObjectClass objectClass, ScimContext context, Set<String> supportedAttributes) {
        super(objectClass, context, supportedAttributes);
    }

    @Override
    public boolean requiresOriginalState() {
        return true;
    }

    @Override
    protected void doUpdate(UpdateRequest request, Set<AttributeDelta> deltas,
                            RestObjectClassDefinition objectClassDef, URI resourceUri) {
        GenericScimResource scimResource = buildScimResource(deltas, objectClassDef, request.before());
        scimResource.setId(request.uid().getUidValue());
        try {
            context.scimClient().replaceRequest(resourceUri, scimResource).invoke(GenericScimResource.class);
        } catch (ScimException e) {
            // Normally the error filter maps the HTTP error first; this covers the rare
            // case where the SDK still produces its own typed exception.
            throw new ConnectorException(
                    "SCIM PUT update of object with UID " + request.uid().getUidValue() + " at " + resourceUri
                            + " failed: " + HttpExceptionMapper.causeMessage(e), e);
        }
    }

    private GenericScimResource buildScimResource(Set<AttributeDelta> deltas, RestObjectClassDefinition objectClass, ConnectorObject before) {
        GenericScimResource scimResource = new GenericScimResource();
        var root = scimResource.getObjectNode();

        for (AttributeDelta delta : deltas) {
            RestAttributeDefinition definition = objectClass.attributeFromConnIdName(delta.getName());
            if (definition == null) {
                continue;
            }
            var mapping = definition.scim();
            if (mapping == null) {
                continue;
            }
            Attribute beforeAttr = before != null ? before.getAttributeByName(delta.getName()) : null;
            Attribute base = beforeAttr != null ? beforeAttr : AttributeBuilder.build(delta.getName());
            Attribute updated = delta.applyTo(base);
            List<Object> values = updated.getValue() != null ? updated.getValue() : List.of();
            JsonNode value = ScimPatchOperations.toValueNode(mapping.wireValues(values));
            setAtPath(root, mapping.scimPath(), value);
        }

        return scimResource;
    }

    /** Sets {@code value} at the (possibly nested, dot-separated) SCIM path under {@code root}. */
    private static void setAtPath(ObjectNode root, String scimPath, JsonNode value) {
        var parts = scimPath.split("\\.");
        ObjectNode current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            JsonNode next = current.get(parts[i]);
            ObjectNode child;
            if (next instanceof ObjectNode objectNode) {
                child = objectNode;
            } else {
                child = JsonNodeFactory.instance.objectNode();
                current.set(parts[i], child);
            }
            current = child;
        }
        current.set(parts[parts.length - 1], value);
    }
}
