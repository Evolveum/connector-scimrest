/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy;

import com.evolveum.polygon.scimrest.ObjectClassHandler;
import com.evolveum.polygon.scimrest.groovy.api.AuthorizationCustomizationBuilder;
import com.evolveum.polygon.scimrest.groovy.api.OperationBuilder;
import com.evolveum.polygon.scimrest.groovy.api.TestOperationBuilder;
import com.evolveum.polygon.scimrest.impl.rest.RestContext;
import groovy.lang.Closure;
import org.identityconnectors.framework.common.objects.ObjectClass;

import java.util.HashMap;
import java.util.Map;

public class RestHandlerBuilder implements OperationBuilder {

    private final ConnectorContext context;
    private final Map<String, BaseOperationSupportBuilder> handlers = new HashMap<>();

    AuthorizationCustomizationBuilderImpl authorization = new AuthorizationCustomizationBuilderImpl();
    TestOperationBuilderImpl testOperation = new TestOperationBuilderImpl();

    public RestHandlerBuilder(ConnectorContext context) {
        this.context = context;
    }

    public BaseOperationSupportBuilder objectClass(String user) {
        return handlers.computeIfAbsent(user, k ->  new BaseOperationSupportBuilder(context,context.schema().objectClass(user)));
    }

    public Map<ObjectClass, ObjectClassHandler> build() {
        Map<ObjectClass, ObjectClassHandler> ret = new HashMap<>();
        for (var builder : handlers.values()) {
            var handler = builder.build();
            if (handler != null) {
                ret.put(handler.objectClass(), handler);
            }
        }
        return ret;
    }

    @Override
    public TestOperationBuilder test(Closure<?> o) {
        return GroovyClosures.callAndReturnDelegate(o, testOperation);
    }

    @Override
    public AuthorizationCustomizationBuilder authorization(Closure<?> o) {
        return GroovyClosures.callAndReturnDelegate(o, authorization);
    }

    public RestContext.AuthorizationCustomizer buildAuthentication() {
        return authorization.build();
    }
}
