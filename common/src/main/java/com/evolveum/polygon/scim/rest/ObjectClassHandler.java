package com.evolveum.polygon.scim.rest;

import com.evolveum.polygon.scim.rest.spi.ObjectClassOperaration;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.Filter;

import java.util.Set;

public interface ObjectClassHandler<HC> {

    /**
     * Checks if specific {@link org.identityconnectors.framework.spi.operations.SPIOperation} is supported for specific object type
     * @param operationType Interface describing ConnId Operation
     * @return
     * @throws UnsupportedOperationException If the SPI Operation is not supported.
     */
    <T extends ObjectClassOperaration<HC>> T  checkSupported(Class<T> operationType) throws UnsupportedOperationException;

    ObjectClass objectClass();
}
