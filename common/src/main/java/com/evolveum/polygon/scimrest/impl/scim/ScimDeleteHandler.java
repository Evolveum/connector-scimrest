/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.scim;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.spi.DeleteOperationHandler;
import com.evolveum.polygon.scimrest.impl.rest.HttpExceptionMapper;
import com.evolveum.polygon.scimrest.impl.rest.HttpStatusMapper;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.exceptions.ConnectorException;

public class ScimDeleteHandler implements DeleteOperationHandler {

    private final ObjectClass objectClass;
    private final ScimContext context;

    public ScimDeleteHandler(ObjectClass objectClass, ScimContext context) {
        this.objectClass = objectClass;
        this.context = context;
    }

    @Override
    public void delete(Uid uid, OperationOptions options, ContextLookup operationContext) {
        var resource = context.resourceForObjectClass(objectClass);
        if (resource == null) {
            throw new ConfigurationException("No SCIM resource mapping for object class: " + objectClass.getObjectClassValue());
        }

        try {
            context.scimClient().deleteRequest(resource.relativeEndpoint(), uid.getUidValue())
                    .invoke();
        } catch (ScimHttpErrorException e) {
            // HTTP 404 means the object is already gone on the resource — midPoint sees
            // UnknownUidException and treats the delete as idempotent success.
            throw ScimExceptionMapper.map(e, HttpStatusMapper.OperationKind.DELETE, uid.getUidValue());
        } catch (ConnectorException e) {
            // ICF type was already set at the boundary (e.g. mapped network failure) — never re-wrap.
            throw e;
        } catch (Exception e) {
            throw new ConnectorException(
                    "Failed to delete SCIM resource with UID " + uid.getUidValue() + " at " + resource.relativeEndpoint()
                            + ": " + HttpExceptionMapper.causeMessage(e), e);
        }
    }
}
