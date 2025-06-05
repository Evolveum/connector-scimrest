package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.CompositeObjectClassHandler;
import com.evolveum.polygon.scim.rest.ObjectClassHandler;
import com.evolveum.polygon.scim.rest.groovy.api.OperationSupportBuilder;
import com.evolveum.polygon.scim.rest.schema.MappedObjectClass;
import com.evolveum.polygon.scim.rest.spi.ExecuteQueryProcessor;
import com.evolveum.polygon.scim.rest.spi.ObjectClassOperation;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

import java.util.HashMap;
import java.util.Map;

public class BaseOperationSupportBuilder implements OperationSupportBuilder {

    private final MappedObjectClass objectClass;
    final ConnectorContext context;

    ObjectClassHandler product;

    Map<Class<? extends ObjectClassOperation>, ObjectClassOperation> buildedOperations = new HashMap<>();

    RestSearchOperationBuilder searchOpBuilder;


    public BaseOperationSupportBuilder(ConnectorContext context, MappedObjectClass restObjectClass) {
        this.objectClass = restObjectClass;
        this.context = context;
        searchOpBuilder = new RestSearchOperationBuilder(this);
    }

    public MappedObjectClass getObjectClass() {
        return objectClass;
    }

    @Override
    public RestSearchOperationBuilder search(@DelegatesTo(RestSearchOperationBuilder.class) Closure o) {
        return GroovyClosures.callAndReturnDelegate(o, searchOpBuilder);
    }

    public BaseOperationSupportBuilder search(ExecuteQueryProcessor processor) {
        buildedOperations.put((Class) ExecuteQueryProcessor.class, processor);
        return this;
    }

    public ObjectClassHandler build() {
        buildOperationIfEmpty(ExecuteQueryProcessor.class, searchOpBuilder);


        return new CompositeObjectClassHandler(objectClass.objectClass(), buildedOperations);
    }

    private void buildOperationIfEmpty(Class<? extends ObjectClassOperation> type, RestSearchOperationBuilder builder) {
        if (builder == null || buildedOperations.containsKey(type)) {
            // Skip building for now
            return;

        }
        buildedOperations.put(type, builder.build());
    }

    public RestSearchOperationBuilder searchBuilder() {
        return searchOpBuilder;
    }
}
