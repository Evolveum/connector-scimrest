/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.scim;

import com.evolveum.polygon.conndev.build.api.UpdateOperationBuilder.UpdateRequest;
import com.evolveum.polygon.scimrest.schema.RestObjectClassDefinition;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import org.identityconnectors.framework.common.objects.AttributeDelta;
import org.identityconnectors.framework.common.objects.ObjectClass;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.util.Map;
import java.util.Set;

/**
 * SCIM update via the {@code PATCH} operation (RFC 7644 section 3.5.2): the changed attributes are
 * translated into {@code add}/{@code replace}/{@code remove} operations and sent as one or more
 * {@code PATCH} requests. The original resource state is not read.
 */
public class ScimPatchUpdateHandler extends AbstractScimUpdateHandler {

    /** SCIM media type for request/response bodies (RFC 7643 section 3). */
    private static final String SCIM_MEDIA_TYPE = "application/scim+json";

    private final Map<String, ScimPatchOperations.PatchAttrConfig> patchConfig;

    public ScimPatchUpdateHandler(ObjectClass objectClass, ScimContext context, Set<String> supportedAttributes,
                                  Map<String, ScimPatchOperations.PatchAttrConfig> patchConfig) {
        super(objectClass, context, supportedAttributes);
        this.patchConfig = patchConfig;
    }

    @Override
    public boolean requiresOriginalState() {
        return false;
    }

    @Override
    protected void doUpdate(UpdateRequest request, Set<AttributeDelta> deltas,
                            RestObjectClassDefinition objectClassDef, URI resourceUri) {
        for (var ops : ScimPatchOperations.build(deltas, objectClassDef, patchConfig)) {
            sendPatch(resourceUri, ScimPatchOperations.toPatchBody(ops));
        }
    }

    private void sendPatch(URI resourceUri, ObjectNode body) {
        // resourceUri is relative to the SCIM base URL (e.g. /Users/123); the top-level client has
        // no base to resolve against, so build the absolute URL explicitly.
        var base = context.scimBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        context.httpClient().target(URI.create(base + resourceUri))
                .request()
                .header("Content-Type", SCIM_MEDIA_TYPE)
                .accept(SCIM_MEDIA_TYPE, "application/json")
                .method("PATCH", Entity.entity(body.toString(), MediaType.valueOf(SCIM_MEDIA_TYPE)))
                .close();
    }
}
