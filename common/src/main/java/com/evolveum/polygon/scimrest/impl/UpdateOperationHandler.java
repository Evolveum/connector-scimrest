package com.evolveum.polygon.scimrest.impl;

import com.evolveum.polygon.scimrest.groovy.api.RestUpdateOperationBuilder;
import org.identityconnectors.framework.common.objects.AttributeDelta;
import org.identityconnectors.framework.common.objects.OperationOptions;

public interface UpdateOperationHandler extends AttributeAwareOperationHandler<AttributeDelta,UpdateOperationHandler> {


    /**
     * Returns True if Operation Handler requires to know original state of supported attributes
     * Otherwise False. Returing True may result in issuing get operations before.
     * @return
     */
    boolean requiresOriginalState();


    void update(RestUpdateOperationBuilder.UpdateRequest request, OperationOptions options);
}
