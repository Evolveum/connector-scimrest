package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.CompositeObjectClassHandler;
import com.evolveum.polygon.scim.rest.ObjectClassHandler;
import com.evolveum.polygon.scim.rest.schema.RestObjectClass;
import com.evolveum.polygon.scim.rest.spi.ExecuteQueryProcessor;
import com.evolveum.polygon.scim.rest.spi.ObjectClassOperation;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

import java.util.HashMap;
import java.util.Map;

public class ObjectClassHandlerBuilder<HC> {

    private final RestObjectClass objectClass;

    ObjectClassHandler<HC> product;

    Map<Class<? extends ObjectClassOperation<?>>, ObjectClassOperation<?>> buildedOperations = new HashMap<>();

    RestSearchOperationBuilder searchOpBuilder;


    public ObjectClassHandlerBuilder(RestObjectClass restObjectClass) {
        this.objectClass = restObjectClass;
        searchOpBuilder = new RestSearchOperationBuilder(this);
    }

    public RestObjectClass getObjectClass() {
        return objectClass;
    }

    public RestSearchOperationBuilder search(@DelegatesTo(RestSearchOperationBuilder.class) Closure o) {
        return GroovyClosures.callAndReturnDelegate(o, searchOpBuilder);
    }

    public ObjectClassHandlerBuilder<HC> search(FilterRequestProcessor processor) {
        buildedOperations.put((Class) ExecuteQueryProcessor.class, RequestProcessorBasedSearch.from(objectClass,processor));
        return this;

    }

    public ObjectClassHandler<HC> build() {
        buildOperationIfEmpty(ExecuteQueryProcessor.class, searchOpBuilder);


        return new CompositeObjectClassHandler<>(objectClass.objectClass(), buildedOperations);
    }

    private <O extends ObjectClassOperation<HC>>void buildOperationIfEmpty(Class<O> type, RestSearchOperationBuilder builder) {
        if (builder == null || buildedOperations.containsKey(type)) {
            // Skip building for now
            return;

        }
        buildedOperations.put(type, builder.build());
    }
}
