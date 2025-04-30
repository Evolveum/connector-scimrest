package com.evolveum.polygon.scim.rest;

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.Filter;

import java.util.Set;

public interface ObjectClassHandler {

    /**
     * Checks if specific {@link org.identityconnectors.framework.spi.operations.SPIOperation} is supported for specific object type
     * @param operationType Interface describing ConnId Operation
     * @return
     * @throws UnsupportedOperationException If the SPI Operation is not supported.
     */
    ObjectClassHandler checkSupported(Class<?> operationType) throws UnsupportedOperationException;

    Uid authenticate(String username, GuardedString password, OperationOptions options);

    /**
     *
     * @param createAttributes
     * @param options
     * @return
     */
    Uid create(Set<Attribute> createAttributes, OperationOptions options);

    Set<AttributeDelta> updateDelta(Uid uid, Set<AttributeDelta> modifications, OperationOptions options);

    void delete(Uid uid, OperationOptions options);

    Uid resolveUsername(String username, OperationOptions options);

    void executeQuery(Filter query, ResultsHandler handler, OperationOptions options);

    ObjectClass objectClass();
}
