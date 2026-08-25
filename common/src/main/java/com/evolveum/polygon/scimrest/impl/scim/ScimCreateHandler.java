/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.scim;

import com.evolveum.polygon.conndev.api.AttributePath;
import com.evolveum.polygon.scimrest.groovy.connector.RestConnectorContext;
import com.evolveum.polygon.conndev.spi.CreateOperationHandler;
import com.evolveum.polygon.scimrest.schema.RestObjectClassDefinition;
import com.unboundid.scim2.common.GenericScimResource;
import com.unboundid.scim2.common.types.AttributeDefinition;
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

        var connectorContext = context.contextLookup().get(RestConnectorContext.class);
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
            if (findScimDefinition(resource, attr.getName()) != null) {
                supported.add(attr);
            }
        }
        return new Capability<>(this, supported);
    }

    /**
     * Resolve the SCIM attribute definition for a ConnId attribute name. Attributes that
     * are mapped by a plain SCIM name are found directly; attributes mapped by a deeper SCIM
     * path (e.g. flattened {@code name_formatted} &rarr; {@code name.formatted}) are resolved
     * through the object class definition's SCIM mapping path.
     */
    private AttributeDefinition findScimDefinition(
            ScimResourceContext resource, String connIdAttributeName) {
        var direct = resource.findAttributeDefinition(AttributePath.of(connIdAttributeName));
        if (direct != null) {
            return direct;
        }
        var connectorContext = context.contextLookup().get(RestConnectorContext.class);
        var definition = connectorContext.schema().objectClass(objectClass.getObjectClassValue())
                .attributeFromConnIdName(connIdAttributeName);
        if (definition == null) {
            return null;
        }
        var scim = definition.scim();
        if (scim == null || scim.path() == null) {
            return null;
        }
        return resource.findAttributeDefinition(scim.path());
    }

    private GenericScimResource buildScimResource(Set<Attribute> attributes, RestObjectClassDefinition objectClass) {
        var scimResource = new GenericScimResource();

        for (var attribute : attributes) {
            var definition = objectClass.attributeFromConnIdName(attribute.getName());
            if (definition != null) {
                var jsonMapping = definition.scim();
                if (jsonMapping != null) {
                    jsonMapping.toJsonNode(attribute, scimResource.getObjectNode());
                }
            }
        }

        return scimResource;
    }

    private ConnectorObject deserializeToObject(GenericScimResource scimResource, RestObjectClassDefinition objectClass) {
        var builder = objectClass.newObjectBuilder();
        for (var attributeDef : objectClass.attributes()) {
            var mapping = attributeDef.scim();
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
