/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.scim;

import com.evolveum.polygon.conndev.api.AttributePath;
import com.evolveum.polygon.conndev.json.JsonAttributeMapping;
import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.scimrest.groovy.ConnectorContext;
import com.evolveum.polygon.scimrest.impl.CreateOperationHandler;
import com.evolveum.polygon.scimrest.schema.MappedObjectClass;
import com.unboundid.scim2.common.GenericScimResource;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.exceptions.ConnectorException;

import java.util.*;

public class ScimCreateHandler implements CreateOperationHandler {

    private final ObjectClass objectClass;
    private final ScimContext context;

    public ScimCreateHandler(ObjectClass objectClass, ScimContext context) {
        this.objectClass = objectClass;
        this.context = context;
    }

    @Override
    public Result create(Set<Attribute> createAttributes, OperationOptions options) {
        var resource = context.resourceForObjectClass(objectClass);
        if (resource == null) {
            throw new IllegalStateException("No SCIM resource mapping for object class: " + objectClass.getObjectClassValue());
        }

        var connectorContext = context.contextLookup().get(ConnectorContext.class);
        var schema = connectorContext.schema();
        var objectClassDef = schema.objectClass(objectClass.getObjectClassValue());
        
        var scimResource = buildScimResource(createAttributes, objectClassDef);
        
        GenericScimResource created;
        try {
            created = context.scimClient().createRequest(resource.relativeEndpoint(), scimResource)
                    .invoke(GenericScimResource.class);
        } catch (Exception e) {
            throw new ConnectorException("Failed to create SCIM resource: " + e.getMessage(), e);
        }

        var connectorObject = deserializeToObject(created, objectClassDef);
        return new Result(objectClass, connectorObject.getUid(), connectorObject);
    }

    @Override
    public Capability<Attribute, CreateOperationHandler> canHandle(Collection<Attribute> request, OperationOptions options) {
        var resource = context.resourceForObjectClass(objectClass);
        if (resource == null) {
            return new Capability<>(null, Collections.emptyList());
        }

        var supported = new ArrayList<Attribute>();
        for (var attr : request) {
            var path = AttributePath.of(attr.getName());
            var definition = resource.findAttributeDefinition(path);
            if (definition != null) {
                supported.add(attr);
            }
        }
        return new Capability<>(this, supported);
    }

    private GenericScimResource buildScimResource(Set<Attribute> attributes, MappedObjectClass objectClass) {
        var scimResource = new GenericScimResource();
        
        for (var attribute : attributes) {
            var definition = objectClass.attributeFromConnIdName(attribute.getName());
            if (definition != null) {
                var jsonMapping = definition.mapping(JsonAttributeMapping.class);
                if (jsonMapping != null) {
                    jsonMapping.toJsonNode(attribute, scimResource.getObjectNode());
                }
            }
        }
        
        return scimResource;
    }

    private ConnectorObject deserializeToObject(GenericScimResource scimResource, MappedObjectClass objectClass) {
        var builder = objectClass.newObjectBuilder();
        for (var attributeDef : objectClass.attributes()) {
            var mapping = attributeDef.mapping(JsonAttributeMapping.class);
            if (mapping != null) {
                var value = mapping.valuesFromObject(scimResource.getObjectNode());
                if (value != null) {
                    builder.addAttribute(attributeDef.attributeOf(value));
                }
            }
        }
        
        var uid = scimResource.getId();
        if (uid != null) {
            builder.setUid(new Uid(uid));
        }
        
        return builder.build();
    }
}
