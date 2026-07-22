/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.scim;

import com.evolveum.polygon.conndev.api.AttributePath;
import com.evolveum.polygon.conndev.json.JsonAttributeMapping;
import com.evolveum.polygon.scimrest.groovy.ConnectorContext;
import com.evolveum.polygon.scimrest.groovy.api.RestUpdateOperationBuilder;
import com.evolveum.polygon.scimrest.impl.UpdateOperationHandler;
import com.evolveum.polygon.scimrest.schema.MappedAttribute;
import com.evolveum.polygon.scimrest.schema.MappedObjectClass;
import com.unboundid.scim2.common.GenericScimResource;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.exceptions.ConnectorException;

import java.net.URI;
import java.util.*;

public class ScimUpdateHandler implements UpdateOperationHandler {

    private final ObjectClass objectClass;
    private final ScimContext context;

    public ScimUpdateHandler(ObjectClass objectClass, ScimContext context) {
        this.objectClass = objectClass;
        this.context = context;
    }

    @Override
    public boolean requiresOriginalState() {
        return true;
    }

    @Override
    public void update(RestUpdateOperationBuilder.UpdateRequest request, OperationOptions options) {
        ScimResourceContext resource = context.resourceForObjectClass(objectClass);
        if (resource == null) {
            throw new IllegalStateException("No SCIM resource mapping for object class: " + objectClass.getObjectClassValue());
        }

        ConnectorContext connectorContext = context.contextLookup().get(ConnectorContext.class);
        var schema = connectorContext.schema();
        var objectClassDef = schema.objectClass(objectClass.getObjectClassValue());

        Set<AttributeDelta> deltas = request.attributeDeltaSet() instanceof Set ? (Set<AttributeDelta>) request.attributeDeltaSet() : new HashSet<>(request.attributeDeltaSet());
        GenericScimResource scimResource = buildScimResource(deltas, objectClassDef, request.before());
        scimResource.setId(request.uid().getUidValue());
        
        URI resourceUri = relativeEndpoint(resource.relativeEndpoint(), request.uid().getUidValue());
        
        try {
            context.scimClient().replaceRequest(resourceUri, scimResource);
        } catch (Exception e) {
            throw new ConnectorException("Failed to update SCIM resource: " + e.getMessage(), e);
        }
    }

    @Override
    public Capability<AttributeDelta, UpdateOperationHandler> canHandle(Collection<AttributeDelta> request, OperationOptions options) {
        ScimResourceContext resource = context.resourceForObjectClass(objectClass);
        if (resource == null) {
            return new Capability<>(null, Collections.emptyList());
        }

        List<AttributeDelta> supported = new ArrayList<>();
        for (AttributeDelta delta : request) {
            AttributePath path = AttributePath.of(delta.getName());
            var definition = resource.findAttributeDefinition(path);
            if (definition != null) {
                supported.add(delta);
            }
        }
        return new Capability<>(this, supported);
    }

    private GenericScimResource buildScimResource(Set<AttributeDelta> deltas, MappedObjectClass objectClass, ConnectorObject before) {
        GenericScimResource scimResource = new GenericScimResource();
        
        for (AttributeDelta delta : deltas) {
            AttributePath path = AttributePath.of(delta.getName());
            MappedAttribute definition = objectClass.attributeFromConnIdName(delta.getName());
            if (definition != null) {
                JsonAttributeMapping jsonMapping = definition.mapping(JsonAttributeMapping.class);
                if (jsonMapping != null) {
                    AttributeBuilder attrBuilder = new AttributeBuilder();
                    attrBuilder.setName(delta.getName());
                    Attribute attr = null;
                    if (before != null) {
                        attr = before.getAttributeByName(delta.getName());
                    }
                    if (attr == null) {
                        attr = AttributeBuilder.build(delta.getName());
                    }
                    Attribute updated = delta.applyTo(attr);
                    jsonMapping.toJsonNode(updated, scimResource.getObjectNode());
                }
            }
        }
        
        return scimResource;
    }

    private URI relativeEndpoint(String endpoint, String id) {
        URI endpointUri = URI.create(endpoint);
        String path = endpointUri.getPath();
        if (!path.endsWith("/")) {
            path = path + "/";
        }
        try {
            return new URI(endpointUri.getScheme(), endpointUri.getAuthority(), path + id, endpointUri.getQuery(), endpointUri.getFragment());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build resource URI", e);
        }
    }
}
