package com.evolveum.polygon.scim.rest;

import com.evolveum.polygon.scim.rest.spi.ObjectClassOperaration;
import org.identityconnectors.framework.common.objects.ObjectClass;

import java.util.HashMap;
import java.util.Map;

public class CompositeObjectClassHandler<HC> implements ObjectClassHandler<HC> {

    private final ObjectClass objectClass;
    private final  Map<Class<? extends ObjectClassOperaration<?>>, ObjectClassOperaration<?>> handlers = new HashMap<>();

    public CompositeObjectClassHandler(ObjectClass objectClass, Map<Class<? extends ObjectClassOperaration<?>>, ObjectClassOperaration<?>> handlers) {
        this.objectClass = objectClass;
        this.handlers.putAll(handlers);
    }

    @Override
    public ObjectClass objectClass() {
        return objectClass;
    }

    @Override
    public <T extends ObjectClassOperaration<HC>> T checkSupported(Class<T> operationType) throws UnsupportedOperationException {
        var rawHandler = handlers.get(operationType);
        if (rawHandler == null) {
            throw new UnsupportedOperationException(String.format("Operation %s is not supported", operationType.getName()));
        }
        return (T) operationType.cast(rawHandler);
    }

}
