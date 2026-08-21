/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy.handler;

import com.evolveum.polygon.scimrest.groovy.schema.BaseOperationSupportBuilder;
import com.evolveum.polygon.scimrest.groovy.auth.AuthorizationCustomizationBuilderImpl;
import com.evolveum.polygon.scimrest.groovy.connector.RestConnectorContext;

import com.evolveum.polygon.conndev.annotations.Script;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;

import com.evolveum.polygon.conndev.spi.ObjectClassHandler;
import com.evolveum.polygon.scimrest.api.AuthorizationCustomizer;
import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.config.ScimClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.api.AuthenticationCustomizationBuilder;
import com.evolveum.polygon.scimrest.groovy.api.OperationBuilder;
import groovy.lang.Closure;
import org.identityconnectors.framework.common.objects.ObjectClass;

import java.util.HashMap;
import java.util.Map;

public class RestHandlerBuilder implements OperationBuilder {

    private final RestConnectorContext context;
    // Keyed by ObjectClass rather than raw String: ConnId's ObjectClass identity is
    // case-insensitive (see ObjectClass.is()/equals()), so "User" and "user" must resolve to the
    // same handler builder instead of silently producing two competing ones.
    private final Map<ObjectClass, BaseOperationSupportBuilder> handlers = new HashMap<>();

    AuthorizationCustomizationBuilderImpl authorization = new AuthorizationCustomizationBuilderImpl();

    public RestHandlerBuilder(RestConnectorContext context) {
        this.context = context;
    }

    public BaseOperationSupportBuilder objectClass(String user) {
        return handlers.computeIfAbsent(new ObjectClass(user),
                k -> new BaseOperationSupportBuilder(context, context.schema().objectClass(user)));
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
    public AuthenticationCustomizationBuilder authentication(@Script.Initialization Closure<?> o) {
        return GroovyClosures.callAndReturnDelegate(o, authentication());
    }

    /** Closure-free entry point for non-Groovy front-ends (e.g. YAML). */
    public AuthenticationCustomizationBuilder authentication() {
        return authorization;
    }

    public AuthorizationCustomizer<RestClientConfiguration> restCustomizer() {
        return authorization.restCustomizer();
    }

    public AuthorizationCustomizer<ScimClientConfiguration> scimCustomizer() {
        return authorization.scimCustomizer();
    }
}
