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
import com.evolveum.polygon.scimrest.groovy.api.RestSearchEndpointBuilder;
import com.evolveum.polygon.scimrest.groovy.api.RestSearchOperationBuilder;

import java.util.ArrayList;

/**
 * Binds a search {@code endpoints:} block. Each list item names an endpoint by its required
 * {@code path}; the item's remaining keys ({@code responseFormat}, {@code objectExtractor},
 * {@code pagingSupport}, {@code singleResult}, {@code emptyFilterSupported}, {@code supportedFilters})
 * are bound onto the {@code RestSearchEndpointBuilder} returned by {@code endpoint(path)}.
 */
public class SearchEndpointsHandler implements CustomYamlHandler {

    @Override
    public void apply(DeclYamlBinder binder, Object target, LocatedNode value) {
        if (!(target instanceof RestSearchOperationBuilder search)) {
            throw new IllegalArgumentException("The search 'endpoints' block requires a search operation builder, got: "
                    + target.getClass().getName());
        }
        for (var item : value.elements()) {
            var path = requireScalar(item, "path");
            var endpoint = search.endpoint(path);
            var rest = new ArrayList<>(item.entries());
            rest.removeIf(e -> e.key().equals("path"));
            binder.bindEntries(rest, endpoint);
        }
    }

    private static String requireScalar(LocatedNode map, String key) {
        var node = map.get(key);
        if (node == null || !node.isValue()) {
            throw new IllegalArgumentException("Missing required key '" + key + "' in search endpoint at "
                    + map.line() + ":" + map.col());
        }
        return node.text();
    }

    @Override
    public void checkGroovySyntax(LocatedNode value, String path, GroovySyntaxChecker checker) {
        if (value == null || value.isNull()) {
            return;
        }
        int i = 0;
        for (var item : value.elements()) {
            var rest = new ArrayList<>(item.entries());
            rest.removeIf(e -> e.key().equals("path"));
            checker.checkFragments(rest, RestSearchEndpointBuilder.class, path + "[" + i + "]");
            i++;
        }
    }
}
