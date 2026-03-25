package com.evolveum.polygon.scimrest.impl;

import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;

public interface DeleteOperationHandler {

    void delete(Uid uid, OperationOptions options);
}
