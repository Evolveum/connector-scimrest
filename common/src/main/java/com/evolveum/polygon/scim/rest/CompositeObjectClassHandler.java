package com.evolveum.polygon.scim.rest;

import com.evolveum.polygon.scim.rest.spi.ObjectClassOperation;
import org.identityconnectors.framework.common.objects.ObjectClass;

import java.util.HashMap;
import java.util.Map;

public class CompositeObjectClassHandler<HC> implements ObjectClassHandler<HC> {

    private final ObjectClass objectClass;
    private final  Map<Class<? extends ObjectClassOperation<?>>, ObjectClassOperation<?>> handlers = new HashMap<>();

    public CompositeObjectClassHandler(ObjectClass objectClass, Map<Class<? extends ObjectClassOperation<?>>, ObjectClassOperation<?>> handlers) {
        this.objectClass = objectClass;
        this.handlers.putAll(handlers);
    }

    @Override
    public ObjectClass objectClass() {
        return objectClass;
    }

    @Override
    public <T extends ObjectClassOperation<HC>> T checkSupported(Class<T> operationType) throws UnsupportedOperationException {
        var rawHandler = handlers.get(operationType);
        if (rawHandler == null) {
            throw new UnsupportedOperationException(String.format("Operation %s is not supported", operationType.getName()));
        }
        return operationType.cast(rawHandler);
    }

}
