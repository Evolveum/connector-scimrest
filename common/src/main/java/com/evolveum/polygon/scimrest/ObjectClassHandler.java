/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest;

import com.evolveum.polygon.scimrest.spi.ObjectClassOperation;
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
