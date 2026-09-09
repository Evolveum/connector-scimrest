/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.yaml.binding;

import com.evolveum.polygon.conndev.api.FilterSpecification;
import com.evolveum.polygon.conndev.yaml.binding.LocatedNode;
import com.evolveum.polygon.conndev.yaml.binding.StructuralHandler;
import com.evolveum.polygon.conndev.yaml.binding.YamlBinder;
import com.evolveum.polygon.scimrest.groovy.api.RestSearchEndpointBuilder;

/**
 * Binds a search endpoint's {@code supportedFilters:} block. Each entry's {@code spec} is a build-time
 * Groovy expression (evaluated against the endpoint, yielding a {@link FilterSpecification}) and its
 * {@code request} is a runtime Groovy block (compiled to a closure that maps the filter onto the HTTP
 * request).
 */
public class SupportedFiltersHandler implements StructuralHandler {

    @Override
    public void apply(YamlBinder binder, Object target, LocatedNode value) {
        if (!(target instanceof RestSearchEndpointBuilder endpoint)) {
            throw new IllegalArgumentException("The 'supportedFilters' block requires a search endpoint, got: "
                    + target.getClass().getName());
        }
        for (var item : value.elements()) {
            var spec = requireScalar(item, "spec");
            var request = requireScalar(item, "request");
            var filterSpec = (FilterSpecification) binder.evaluate(spec, endpoint);
            endpoint.supportedFilter(filterSpec, binder.compileClosure(request));
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
}
