/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.yaml.binding;

import com.evolveum.polygon.conndev.api.FilterSpecification;
import com.evolveum.polygon.conndev.build.api.SearchScriptBuilder;
import com.evolveum.polygon.conndev.yaml.binding.LocatedNode;
import com.evolveum.polygon.conndev.yaml.binding.StructuralHandler;
import com.evolveum.polygon.conndev.yaml.binding.YamlBinder;
import com.evolveum.polygon.scimrest.groovy.api.RestSearchOperationBuilder;

/**
 * Binds a search {@code custom:} block onto the {@code SearchScriptBuilder} returned by {@code
 * custom()}. Each {@code supportedFilters} entry carries a build-time {@code spec} (evaluated against
 * the script builder, yielding a {@link FilterSpecification}); {@code implementation} is the runtime
 * Groovy block (its {@code SearchScriptContext} delegate is applied by the builder at run time).
 */
public class CustomSearchHandler implements StructuralHandler {

    @Override
    public void apply(YamlBinder binder, Object target, LocatedNode value) {
        if (!(target instanceof RestSearchOperationBuilder search)) {
            throw new IllegalArgumentException("The 'custom' block requires a search operation builder, got: "
                    + target.getClass().getName());
        }
        var custom = search.custom();
        for (var entry : value.entries()) {
            var key = entry.key();
            var node = entry.value();
            switch (key) {
                case "supportedFilters" -> bindSupportedFilters(binder, custom, node);
                case "implementation" -> custom.implementation(binder.compileClosure(scalar(node, key)));
                case "emptyFilterSupported" -> custom.emptyFilterSupported(node.asBoolean());
                default -> throw unknown(key, node);
            }
        }
    }

    private static void bindSupportedFilters(YamlBinder binder, SearchScriptBuilder custom, LocatedNode list) {
        for (var item : list.elements()) {
            var spec = item.get("spec");
            if (spec == null || !spec.isValue()) {
                throw new IllegalArgumentException("Missing required key 'spec' in custom supportedFilter at "
                        + item.line() + ":" + item.col());
            }
            custom.supportedFilter((FilterSpecification) binder.evaluate(spec.text(), custom));
        }
    }

    private static String scalar(LocatedNode node, String key) {
        if (!node.isValue()) {
            throw new IllegalArgumentException("Expected a block scalar for '" + key + "' at "
                    + node.line() + ":" + node.col());
        }
        return node.text();
    }

    private static IllegalArgumentException unknown(String key, LocatedNode node) {
        return new IllegalArgumentException("Unknown key '" + key + "' in search custom block at "
                + node.line() + ":" + node.col());
    }
}
