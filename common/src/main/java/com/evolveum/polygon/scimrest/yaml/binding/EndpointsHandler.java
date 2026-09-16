/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.yaml.binding;

import com.evolveum.polygon.conndev.yaml.decl.GroovySyntaxChecker;
import com.evolveum.polygon.conndev.yaml.decl.LocatedNode;
import com.evolveum.polygon.conndev.yaml.decl.CustomYamlHandler;
import com.evolveum.polygon.conndev.yaml.decl.DeclYamlBinder;
import com.evolveum.polygon.scimrest.groovy.api.EndpointBuilder;
import com.evolveum.polygon.scimrest.groovy.api.HttpMethod;
import com.evolveum.polygon.scimrest.groovy.api.RestOperationBuilder;

import java.util.ArrayList;

/**
 * Binds an {@code endpoints:} block (a list of endpoint maps) onto an operation builder. Each
 * list item is a map whose {@code method} (defaults to {@code POST}) and {@code path} keys name the
 * endpoint; the item's remaining keys (e.g. {@code request}, {@code supportedAttributes}) are bound
 * onto the {@code Endpoint} returned by {@code endpoint(method, path)}.
 *
 * <p>{@code method}/{@code path} are consumed here (they select the endpoint) and are not bound as
 * entries, so they must not be re-declared on the endpoint type.
 */
public class EndpointsHandler implements CustomYamlHandler {

    @Override
    public void apply(DeclYamlBinder binder, Object target, LocatedNode value) {
        if (!(target instanceof RestOperationBuilder<?> operation)) {
            throw new IllegalArgumentException("The 'endpoints' block requires an operation builder, got: "
                    + target.getClass().getName());
        }
        for (var entry : value.elements()) {
            var method = readScalar(entry, "method", "POST");
            var path = requireScalar(entry, "path");
            var endpoint = operation.endpoint(HttpMethod.valueOf(method.toUpperCase()), path);
            var rest = new ArrayList<>(entry.entries());
            rest.removeIf(e -> e.key().equals("method") || e.key().equals("path"));
            binder.bindEntries(rest, endpoint);
        }
    }

    private static String readScalar(LocatedNode map, String key, String defaultValue) {
        for (var e : map.entries()) {
            if (e.key().equals(key) && e.value().isValue()) {
                return e.value().text();
            }
        }
        return defaultValue;
    }

    private static String requireScalar(LocatedNode map, String key) {
        for (var e : map.entries()) {
            if (e.key().equals(key) && e.value().isValue()) {
                return e.value().text();
            }
        }
        throw new IllegalArgumentException("Missing required key '" + key + "' in endpoint at "
                + map.line() + ":" + map.col());
    }

    @Override
    public void checkGroovySyntax(LocatedNode value, String path, GroovySyntaxChecker checker) {
        if (value == null || value.isNull()) {
            return;
        }
        int i = 0;
        for (var entry : value.elements()) {
            var rest = new ArrayList<>(entry.entries());
            rest.removeIf(e -> e.key().equals("method") || e.key().equals("path"));
            checker.checkFragments(rest, EndpointBuilder.class, path + "[" + i + "]");
            i++;
        }
    }
}
