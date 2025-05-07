package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.CompositeObjectClassHandler;
import com.evolveum.polygon.scim.rest.ObjectClassHandler;
import com.evolveum.polygon.scim.rest.schema.RestObjectClass;
import com.evolveum.polygon.scim.rest.spi.ExecuteQueryProcessor;
import com.evolveum.polygon.scim.rest.spi.ObjectClassOperation;
import groovy.lang.Closure;
import java.util.HashMap;
import java.util.Map;

public class ObjectClassHandlerBuilder<HC> {

    private final RestObjectClass objectClass;

    ObjectClassHandler<HC> product;

    Map<Class<? extends ObjectClassOperation<?>>, ObjectClassOperation<?>> buildedOperations = new HashMap<>();

    public ObjectClassHandlerBuilder(RestObjectClass restObjectClass) {
        this.objectClass = restObjectClass;
    }

    public SearchOpBuilder search(Closure o) {
        return null;
    }

    public ObjectClassHandlerBuilder<HC> search(FilterRequestProcessor processor) {
        buildedOperations.put((Class) ExecuteQueryProcessor.class, RequestProcessorBasedSearch.from(objectClass,processor));
        return this;

    }

    public ObjectClassHandler<HC> build() {
        return new CompositeObjectClassHandler<>(objectClass.objectClass(), buildedOperations);
    }
}
