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
import com.evolveum.polygon.scimrest.schema.MappedObjectClass;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public class BaseOperationSupportBuilder
        extends BaseObjectOperationSupportBuilder<RestSearchOperationBuilderImpl, RestCreateOperationBuilderImpl, RestUpdateOperationBuilderImpl, RestDeleteOperationBuilderImpl>
        implements ObjectOperationSupportBuilder {

    final RestConnectorContext context;

    RestSearchOperationBuilderImpl searchOpBuilder;

    private final RestCreateOperationBuilderImpl createOpBuilder;
    private final RestUpdateOperationBuilderImpl updateOpBuilder;
    private final RestDeleteOperationBuilderImpl deleteOpBuilder;

    public BaseOperationSupportBuilder(RestConnectorContext context, MappedObjectClass restObjectClass) {
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

    @Override
    public MappedObjectClass getObjectClass() {
        return (MappedObjectClass) super.getObjectClass();
    }

    public RestSearchOperationBuilderImpl searchBuilder() {
        return searchOpBuilder;
    }

    // scimrest's own ObjectOperationSupportBuilder stays independent of conndev's (extending it hits
    // a hard Java limitation elsewhere - see ConnectorBuilder.ObjectClassBuilder, which combines this
    // with the schema builder and cannot inherit Fluent<F> with two different F). Because this class
    // extends BaseObjectOperationSupportBuilder (which itself implements conndev's ObjectOperationSupportBuilder),
    // it still ends up with two unrelated sources for these six Closure-based methods, so Java requires
    // an explicit override to resolve the ambiguity.

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
