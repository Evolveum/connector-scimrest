/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.scimrest.impl;

import com.evolveum.polygon.scimrest.spi.DeleteOperation;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;

import java.util.Collection;

public class DeleteOperationStrategyHandler implements DeleteOperation {

    private final Collection<DeleteOperationHandler> handlers;

    public DeleteOperationStrategyHandler(Collection<DeleteOperationHandler> handlers) {
        this.handlers = handlers;
    }

    @Override
    public void delete(Uid uid, OperationOptions options) {
        if (handlers == null || handlers.isEmpty()) {
            throw new ConnectorException("No delete handler configured.");
        }
        for (DeleteOperationHandler handler : handlers) {
            handler.delete(uid, options);
            return;
        }
    }
}
