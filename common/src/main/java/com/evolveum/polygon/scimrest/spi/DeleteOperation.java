package com.evolveum.polygon.scimrest.spi;

import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;

public interface DeleteOperation extends ObjectClassOperation {

    void delete(Uid uid, OperationOptions options);
}
