/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy.api;

import com.evolveum.polygon.scimrest.groovy.GroovyClosures;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public interface ObjectOperationSupportBuilder {

    RestSearchOperationBuilder search();

    RestListOperationBuilder list();

    ReadOperationBuilder read();

    RestCreateOperationBuilder create();

    RestUpdateOperationBuilder update();

    RestDeleteOperationBuilder delete();

    default RestSearchOperationBuilder search(@DelegatesTo(value = RestSearchOperationBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, search());
    }

    default RestListOperationBuilder list(@DelegatesTo(value = RestListOperationBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, list());
    };

    default ReadOperationBuilder read(@DelegatesTo(value = ReadOperationBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, read());
    }

    default RestCreateOperationBuilder create(@DelegatesTo(value = RestCreateOperationBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, create());
    }

    default RestUpdateOperationBuilder update(@DelegatesTo(value = RestUpdateOperationBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, update());
    }

    default RestDeleteOperationBuilder delete(@DelegatesTo(value = RestDeleteOperationBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, delete());
    }
}
