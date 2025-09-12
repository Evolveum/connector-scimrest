/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy;

import com.evolveum.polygon.scimrest.CompositeObjectClassHandler;
import com.evolveum.polygon.scimrest.ObjectClassHandler;
import com.evolveum.polygon.scimrest.groovy.api.AuthorizationCustomizationBuilder;
import com.evolveum.polygon.scimrest.groovy.api.ObjectOperationSupportBuilder;
import com.evolveum.polygon.scimrest.groovy.api.TestOperationBuilder;
import com.evolveum.polygon.scimrest.schema.MappedObjectClass;
import com.evolveum.polygon.scimrest.spi.ExecuteQueryProcessor;
import com.evolveum.polygon.scimrest.spi.ObjectClassOperation;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

import java.util.HashMap;
import java.util.Map;

public class BaseOperationSupportBuilder implements ObjectOperationSupportBuilder {

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
