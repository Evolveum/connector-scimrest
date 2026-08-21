/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy;

import com.evolveum.polygon.conndev.build.api.ListOperationBuilder;
import com.evolveum.polygon.conndev.build.api.ReadOperationBuilder;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import com.evolveum.polygon.conndev.groovy.BaseObjectOperationSupportBuilder;
import com.evolveum.polygon.scimrest.groovy.api.*;
import com.evolveum.polygon.scimrest.schema.RestObjectClassDefinition;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public class BaseOperationSupportBuilder
        extends BaseObjectOperationSupportBuilder<RestSearchOperationBuilderImpl, RestCreateOperationBuilderImpl, RestUpdateOperationBuilderImpl, RestDeleteOperationBuilderImpl, RestObjectClassDefinition> {

    final RestConnectorContext context;

    RestSearchOperationBuilderImpl searchOpBuilder;

    private final RestCreateOperationBuilderImpl createOpBuilder;
    private final RestUpdateOperationBuilderImpl updateOpBuilder;
    private final RestDeleteOperationBuilderImpl deleteOpBuilder;

    public BaseOperationSupportBuilder(RestConnectorContext context, RestObjectClassDefinition restObjectClass) {
        super(context, restObjectClass);
        this.context = context;

        searchOpBuilder = new RestSearchOperationBuilderImpl(this);
        createOpBuilder = new RestCreateOperationBuilderImpl(this);
        updateOpBuilder = new RestUpdateOperationBuilderImpl(this);
        deleteOpBuilder = new RestDeleteOperationBuilderImpl(this);
    }

    @Override
    public ListOperationBuilder list() {
        // FIXME: Implement
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    @Override
    public ReadOperationBuilder read() {
        // FIXME: Implement
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    @Override
    public RestSearchOperationBuilderImpl search() {
        return searchOpBuilder;
    }

    @Override
    public RestCreateOperationBuilderImpl create() {
        return createOpBuilder;
    }

    @Override
    public RestUpdateOperationBuilderImpl update() {
        return updateOpBuilder;
    }

    @Override
    public RestDeleteOperationBuilderImpl delete() {
        return deleteOpBuilder;
    }

    public RestSearchOperationBuilderImpl searchBuilder() {
        return searchOpBuilder;
    }

    // Narrows the inherited closure-based default methods (conndev's ObjectOperationSupportBuilder,
    // implemented by BaseObjectOperationSupportBuilder) to REST-specific return types, matching the
    // no-arg overrides above.

    @Override
    public RestSearchOperationBuilder search(@DelegatesTo(value = RestSearchOperationBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, search());
    }

    @Override
    public ListOperationBuilder list(@DelegatesTo(value = ListOperationBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, list());
    }

    @Override
    public ReadOperationBuilder read(@DelegatesTo(value = ReadOperationBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, read());
    }

    @Override
    public RestCreateOperationBuilder create(@DelegatesTo(value = RestCreateOperationBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, create());
    }

    @Override
    public RestUpdateOperationBuilder update(@DelegatesTo(value = RestUpdateOperationBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, update());
    }

    @Override
    public RestDeleteOperationBuilder delete(@DelegatesTo(value = RestDeleteOperationBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, delete());
    }
}
