package com.evolveum.polygon.scim.rest;

import com.evolveum.polygon.scim.rest.spi.ObjectClassOperation;
import org.identityconnectors.framework.common.objects.*;

public interface ObjectClassHandler<HC> {

    /**
     * Checks if specific {@link org.identityconnectors.framework.spi.operations.SPIOperation} is supported for specific object type
     * @param operationType Interface describing ConnId Operation
     * @return
     * @throws UnsupportedOperationException If the SPI Operation is not supported.
     */
    <T extends ObjectClassOperation<HC>> T  checkSupported(Class<T> operationType) throws UnsupportedOperationException;

    ObjectClass objectClass();
}
