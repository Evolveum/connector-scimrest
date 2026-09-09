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
import com.evolveum.polygon.scimrest.groovy.api.RestSearchOperationBuilder;

/**
 * Binds a search {@code normalize:} block onto the {@code NormalizationBuilder} returned by
 * {@code normalize()}. {@code toSingleValue} is a plain attribute name; the {@code rewriteUid},
 * {@code rewriteName}, {@code restoreUid} and {@code restoreName} entries are runtime Groovy blocks
 * compiled to closures (their {@code RewriteContext} delegate is applied by the builder at run time).
 */
public class NormalizationHandler implements StructuralHandler {

    @Override
    public void apply(YamlBinder binder, Object target, LocatedNode value) {
        if (!(target instanceof RestSearchOperationBuilder search)) {
            throw new IllegalArgumentException("The 'normalize' block requires a search operation builder, got: "
                    + target.getClass().getName());
        }
        var normalize = search.normalize();
        for (var entry : value.entries()) {
            var key = entry.key();
            var node = entry.value();
            switch (key) {
                case "toSingleValue" -> normalize.toSingleValue(scalar(node, key));
                case "rewriteUid" -> normalize.rewriteUid(binder.compileClosure(scalar(node, key)));
                case "rewriteName" -> normalize.rewriteName(binder.compileClosure(scalar(node, key)));
                case "restoreUid" -> normalize.restoreUid(binder.compileClosure(scalar(node, key)));
                case "restoreName" -> normalize.restoreName(binder.compileClosure(scalar(node, key)));
                default -> throw unknown(key, node);
            }
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
        return new IllegalArgumentException("Unknown key '" + key + "' in search normalize block at "
                + node.line() + ":" + node.col());
    }
}
