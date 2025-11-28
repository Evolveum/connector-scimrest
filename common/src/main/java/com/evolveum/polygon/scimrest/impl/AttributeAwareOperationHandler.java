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
