/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.scimrest.impl;

import org.identityconnectors.framework.common.objects.OperationOptions;

import java.util.Collection;

public interface AttributeAwareOperationHandler<R, H extends AttributeAwareOperationHandler<R, H>> {

    Capability<R,H> canHandle(Collection<R> request, OperationOptions options);

    record Capability<R,H>(H handler, Collection<R> supported) {

        boolean isUnsupported() {
            return supported == null || supported.isEmpty();
        }
    }
}
