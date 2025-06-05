package com.evolveum.polygon.scim.rest;

import com.evolveum.polygon.scim.rest.spi.ObjectClassOperation;
import org.identityconnectors.framework.common.objects.*;

/**
 * Defines a contract for handling operations and object classes in the context of a specific object type.
 *
 * @param <HC> the type of the handler context, representing the current context of the connector.
 */
public interface ObjectClassHandler {

    /**
     * Checks if specific {@link org.identityconnectors.framework.spi.operations.SPIOperation} is supported for specific object type
     * @param operationType Interface describing ConnId Operation
     * @return
     * @throws UnsupportedOperationException If the SPI Operation is not supported.
     */
    <T extends ObjectClassOperation> T  checkSupported(Class<T> operationType) throws UnsupportedOperationException;

    ObjectClass objectClass();
}
