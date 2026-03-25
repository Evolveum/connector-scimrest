/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.scim;

import com.evolveum.polygon.scimrest.impl.DeleteOperationHandler;
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
    public void delete(Uid uid, OperationOptions options) {
        var resource = context.resourceForObjectClass(objectClass);
        if (resource == null) {
            throw new IllegalStateException("No SCIM resource mapping for object class: " + objectClass.getObjectClassValue());
        }

        try {
            context.scimClient().deleteRequest(resource.relativeEndpoint(), uid.getUidValue())
                    .invoke();
        } catch (Exception e) {
            throw new ConnectorException("Failed to delete SCIM resource: " + e.getMessage(), e);
        }
    }
}
