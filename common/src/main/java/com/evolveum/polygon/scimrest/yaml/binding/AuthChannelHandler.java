/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.yaml.binding;

import com.evolveum.polygon.conndev.yaml.binding.LocatedNode;
import com.evolveum.polygon.conndev.yaml.binding.StructuralHandler;
import com.evolveum.polygon.conndev.yaml.binding.YamlBinder;
import com.evolveum.polygon.scimrest.groovy.api.AuthenticationCustomizationBuilder;
import com.evolveum.polygon.scimrest.groovy.api.AuthenticationCustomizationBuilder.OAuth2Builder;
import groovy.lang.Closure;

/**
 * Base for the engine-driven auth channel handlers (the {@code rest}/ {@code scim} blocks under
 * {@code authentication}). Ports the logic of the former POJO {@code YamlAuthenticationHandler} onto
 * the location-aware engine: each known method key is bound onto the live channel builder, reusing the
 * Groovy runtime so every block (implementation, OAuth2 token-flow hooks) is compiled to a closure.
 *
 * <p>Structural handlers are instantiated once per binding and reused, so this type is stateless — all
 * per-document data flows in through {@link #apply(YamlBinder, Object, LocatedNode)}.
 */
abstract class AuthChannelHandler implements StructuralHandler {

    @Override
    public final void apply(YamlBinder binder, Object target, LocatedNode channel) {
        if (!(target instanceof AuthenticationCustomizationBuilder auth)) {
            throw new IllegalArgumentException("The authentication channel block requires the authentication builder, got: "
                    + target.getClass().getName());
        }
        bind(binder, auth, channel);
    }

    protected abstract void bind(YamlBinder binder, AuthenticationCustomizationBuilder auth, LocatedNode channel);

    /** The text of a scalar/block child of {@code map}, or {@code null} when absent. */
    protected static String block(LocatedNode map, String key) {
        var node = map.get(key);
        return (node != null && node.isValue()) ? node.text() : null;
    }

    /**
     * A driver closure handed to an {@code oauth2*(Closure)} method: when invoked (with the
     * {@link OAuth2Builder} as delegate) it sets each configured token-flow hook from a compiled snippet.
     */
    protected static Closure<?> oauth2Driver(YamlBinder binder, LocatedNode node) {
        return new Closure<Object>(AuthChannelHandler.class) {
            public Object doCall(Object context) {
                var builder = (OAuth2Builder) getDelegate();
                var hook = block(node, "buildTokenRequest");
                if (hook != null) {
                    builder.buildTokenRequest(binder.compileClosure(hook, "request"));
                }
                hook = block(node, "parseTokenResponse");
                if (hook != null) {
                    builder.parseTokenResponse(binder.compileClosure(hook, "response"));
                }
                hook = block(node, "validateToken");
                if (hook != null) {
                    builder.validateToken(binder.compileClosure(hook, "token"));
                }
                hook = block(node, "applyToken");
                if (hook != null) {
                    builder.applyToken(binder.compileClosure(hook, "request"));
                }
                hook = block(node, "onResponse");
                if (hook != null) {
                    builder.onResponse(binder.compileClosure(hook, "response"));
                }
                var impl = block(node, "implementation");
                if (impl != null) {
                    builder.implementation(binder.compileClosure(impl));
                }
                return null;
            }
        };
    }

    protected static IllegalArgumentException unknownMethod(LocatedNode method, String key) {
        return new IllegalArgumentException("Unknown auth method '" + key + "' at "
                + method.line() + ":" + method.col());
    }
}
