/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.scimrest.groovy.api;

import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public interface OperationBuilder {

    ObjectOperationSupportBuilder objectClass(String className);

    default ObjectOperationSupportBuilder objectClass(String className, @DelegatesTo(value = ObjectOperationSupportBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, objectClass(className));
    }

    TestOperationBuilder test(@DelegatesTo(TestOperationBuilder.class) Closure<?> o);

    AuthenticationCustomizationBuilder authentication(@DelegatesTo(AuthenticationCustomizationBuilder.class) Closure<?> o);

}
