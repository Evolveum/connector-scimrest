/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.scim;

import com.evolveum.polygon.conndev.api.AttributePath;
import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.build.api.UpdateOperationBuilder.UpdateRequest;
import com.evolveum.polygon.scimrest.groovy.connector.RestConnectorContext;
import com.evolveum.polygon.conndev.spi.UpdateOperationHandler;
import com.evolveum.polygon.scimrest.schema.RestObjectClassDefinition;
import com.unboundid.scim2.common.exceptions.ScimException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.*;

import java.net.URI;
import java.util.*;

/**
 * Base for the SCIM update handlers.
 *
 * <p>Both the {@code PUT} (full replacement, {@link ScimPutUpdateHandler}) and {@code PATCH} (partial
 * update, {@link ScimPatchUpdateHandler}) strategies resolve the SCIM resource, the object-class
 * definition and the per-resource URI the same way and share the {@link #canHandle} attribute
 * filtering; they differ only in whether the original state is required and in how the request is
 * built and sent, which is delegated to {@link #doUpdate}.</p>
 */
public abstract class AbstractScimUpdateHandler implements UpdateOperationHandler {

    protected final ObjectClass objectClass;
    protected final ScimContext context;
    protected final Set<String> supportedAttributes;

    protected AbstractScimUpdateHandler(ObjectClass objectClass, ScimContext context, Set<String> supportedAttributes) {
        this.objectClass = objectClass;
        this.context = context;
        this.supportedAttributes = supportedAttributes;
    }

    @Override
    public void update(UpdateRequest request, OperationOptions options, ContextLookup operationContext) {
        ScimResourceContext resource = context.resourceForObjectClass(objectClass);
        if (resource == null) {
            throw new IllegalStateException("No SCIM resource mapping for object class: " + objectClass.getObjectClassValue());
        }

        RestConnectorContext connectorContext = context.contextLookup().get(RestConnectorContext.class);
        RestObjectClassDefinition objectClassDef = connectorContext.schema().objectClass(objectClass.getObjectClassValue());

        URI resourceUri = relativeEndpoint(resource.relativeEndpoint(), request.uid().getUidValue());

        try {
            doUpdate(request, toDeltaSet(request), objectClassDef, resourceUri);
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
            if (definition == null) {
                continue;
            }
            if (supportedAttributes != null && !supportedAttributes.contains(delta.getName())) {
                continue;
            }
            supported.add(delta);
        }
        return new Capability<>(this, supported);
    }

    /**
     * Builds and sends the SCIM request for the given deltas.
     *
     * @param request        the update request (carries the UID and, when present, the original state)
     * @param deltas         the attribute deltas to apply
     * @param objectClassDef the SCIM object-class definition (attribute / path resolution)
     * @param resourceUri    the (relative) URI of the target SCIM resource
     */
    protected abstract void doUpdate(UpdateRequest request, Set<AttributeDelta> deltas,
                                     RestObjectClassDefinition objectClassDef, URI resourceUri) throws ScimException;

    private static Set<AttributeDelta> toDeltaSet(UpdateRequest request) {
        return request.attributeDeltaSet() instanceof Set set
                ? set
                : new HashSet<>(request.attributeDeltaSet());
    }

    /** Builds the resource URI ({@code endpoint}/{id}) from the SCIM relative endpoint and the object UID. */
    private static URI relativeEndpoint(String endpoint, String id) {
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
