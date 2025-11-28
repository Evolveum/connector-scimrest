/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy;

import com.evolveum.polygon.scimrest.CompositeObjectClassHandler;
import com.evolveum.polygon.scimrest.ObjectClassHandler;
import com.evolveum.polygon.scimrest.groovy.api.*;
import com.evolveum.polygon.scimrest.schema.MappedObjectClass;
import com.evolveum.polygon.scimrest.spi.CreateOperation;
import com.evolveum.polygon.scimrest.spi.ExecuteQueryProcessor;
import com.evolveum.polygon.scimrest.spi.ObjectClassOperation;
import com.evolveum.polygon.scimrest.spi.UpdateOperation;

import java.util.HashMap;
import java.util.Map;

public class BaseOperationSupportBuilder implements ObjectOperationSupportBuilder {

    private final MappedObjectClass objectClass;
    final ConnectorContext context;

    ObjectClassHandler product;

    Map<Class<? extends ObjectClassOperation>, ObjectClassOperation> buildedOperations = new HashMap<>();

    RestSearchOperationBuilderImpl searchOpBuilder;

    private final RestCreateOperationBuilderImpl createOpBuilder;
    private final RestUpdateOperationBuilderImpl updateOpBuilder;
    private final RestDeleteOperationBuilderImpl deleteOpBuilder;

    public BaseOperationSupportBuilder(ConnectorContext context, MappedObjectClass restObjectClass) {
        this.objectClass = restObjectClass;
        this.context = context;

        searchOpBuilder = new RestSearchOperationBuilderImpl(this);
        createOpBuilder = new RestCreateOperationBuilderImpl(this);
        updateOpBuilder = new RestUpdateOperationBuilderImpl(this);
        deleteOpBuilder = new RestDeleteOperationBuilderImpl(this);
    }

    @Override
    public RestListOperationBuilder list() {
        // FIXME: Implement
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    @Override
    public ReadOperationBuilder read() {
        // FIXME: Implement
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    @Override
    public RestSearchOperationBuilder search() {
        return searchOpBuilder;
    }

    @Override
    public RestCreateOperationBuilder create() {
        return createOpBuilder;
    }

    @Override
    public RestUpdateOperationBuilder update() {
        return updateOpBuilder;
    }

    @Override
    public RestDeleteOperationBuilder delete() {
        return deleteOpBuilder;
    }

    public MappedObjectClass getObjectClass() {
        return objectClass;
    }


    public BaseOperationSupportBuilder search(ExecuteQueryProcessor processor) {
        buildedOperations.put(ExecuteQueryProcessor.class, processor);
        return this;
    }

    public ObjectClassHandler build() {
        buildOperationIfEmpty(ExecuteQueryProcessor.class, searchOpBuilder);
        buildOperationIfEmpty(CreateOperation.class, createOpBuilder);
        buildOperationIfEmpty(UpdateOperation.class, updateOpBuilder);
        //buildOperationIfEmpty(RestDeleteOperationBuilder.class, deleteOpBuilder);
        return new CompositeObjectClassHandler(objectClass.objectClass(), buildedOperations);
    }

    private  <T extends ObjectClassOperation> void buildOperationIfEmpty(Class<T> type, RestObjectOperationBuilder<T> builder) {
        if (builder == null || buildedOperations.containsKey(type)) {
            // Skip building for now
            return;

        }
        buildedOperations.put(type, builder.build());
    }

    public RestSearchOperationBuilderImpl searchBuilder() {
        return searchOpBuilder;
    }
}
