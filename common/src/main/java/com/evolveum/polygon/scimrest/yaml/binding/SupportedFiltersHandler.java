/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.yaml.binding;

import com.evolveum.polygon.conndev.api.FilterSpecification;
import com.evolveum.polygon.conndev.yaml.decl.GroovySyntaxChecker;
import com.evolveum.polygon.conndev.yaml.decl.LocatedNode;
import com.evolveum.polygon.conndev.yaml.decl.CustomYamlHandler;
import com.evolveum.polygon.conndev.yaml.decl.DeclYamlBinder;
import com.evolveum.polygon.scimrest.groovy.api.RestSearchEndpointBuilder;

/**
 * Binds a search endpoint's {@code supportedFilters:} block. Each entry's {@code spec} is a build-time
 * Groovy expression (evaluated against the endpoint, yielding a {@link FilterSpecification}); its
 * {@code request} (a runtime Groovy block compiled to a closure that maps the filter onto the HTTP
 * request) is optional — without it the entry declares a concrete-value filter the endpoint serves
 * on its own, so a matching search is routed to the endpoint with the request left unmodified.
 */
public class SupportedFiltersHandler implements CustomYamlHandler {

    @Override
    public void apply(DeclYamlBinder binder, Object target, LocatedNode value) {
        if (!(target instanceof RestSearchEndpointBuilder endpoint)) {
            throw new IllegalArgumentException("The 'supportedFilters' block requires a search endpoint, got: "
                    + target.getClass().getName());
        }
        for (var item : value.elements()) {
            var spec = requireScalar(item, "spec");
            var request = optionalScalar(item, "request");
            var filterSpec = (FilterSpecification) binder.evaluate(spec, endpoint);
            if (request == null) {
                endpoint.supportedFilter(filterSpec);
            } else {
                endpoint.supportedFilter(filterSpec, binder.compileClosure(request));
            }
        }
    }

    @Override
    public void checkGroovySyntax(LocatedNode value, String path, GroovySyntaxChecker checker) {
        if (value == null || value.isNull()) {
            return;
        }
        int i = 0;
        for (var item : value.elements()) {
            var itemPath = path + "[" + i + "]";
            checkOptionalField(item, "spec", itemPath, checker);
            checkOptionalField(item, "request", itemPath, checker);
            i++;
        }
    }

    private static void checkOptionalField(LocatedNode item, String key, String itemPath, GroovySyntaxChecker checker) {
        var node = item.get(key);
        if (node != null) {
            checker.checkFragment(node, itemPath + "." + key);
        }
    }

    private static String requireScalar(LocatedNode map, String key) {
        var node = map.get(key);
        if (node == null || !node.isValue()) {
            throw new IllegalArgumentException("Missing required key '" + key + "' in supportedFilter at "
                    + map.line() + ":" + map.col());
        }
        return node.text();
    }

    private static String optionalScalar(LocatedNode map, String key) {
        var node = map.get(key);
        return (node != null && node.isValue()) ? node.text() : null;
    }
}
