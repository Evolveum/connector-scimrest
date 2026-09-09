/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.yaml.binding;

import com.evolveum.polygon.conndev.build.api.AttributeResolverBuilder;
import com.evolveum.polygon.conndev.yaml.decl.LocatedNode;
import com.evolveum.polygon.conndev.yaml.decl.CustomYamlHandler;
import com.evolveum.polygon.conndev.yaml.decl.DeclYamlBinder;
import com.evolveum.polygon.scimrest.groovy.api.RestSearchOperationBuilder;

/**
 * Binds a search {@code attributeResolvers:} list onto the search operation. Each item configures one
 * resolver via a fresh {@code attributeResolver()} builder: {@code attribute} marks the resolved
 * attribute, {@code implementation} is a runtime Groovy block (its
 * {@code AttributeResolutionScriptContext} delegate is applied by the builder at run time) and
 * {@code resolutionType} selects {@code PER_OBJECT}/{@code BATCH} handling.
 */
public class AttributeResolversHandler implements CustomYamlHandler {

    @Override
    public void apply(DeclYamlBinder binder, Object target, LocatedNode value) {
        if (!(target instanceof RestSearchOperationBuilder search)) {
            throw new IllegalArgumentException("The 'attributeResolvers' block requires a search operation builder, got: "
                    + target.getClass().getName());
        }
        for (var item : value.elements()) {
            var resolver = search.attributeResolver();
            for (var entry : item.entries()) {
                var key = entry.key();
                var node = entry.value();
                switch (key) {
                    case "attribute" -> resolver.attribute(scalar(node, key));
                    case "implementation" -> resolver.implementation(binder.compileClosure(scalar(node, key)));
                    case "resolutionType" -> resolver.resolutionType(resolutionType(node, key));
                    default -> throw unknown(key, node);
                }
            }
        }
    }

    private static AttributeResolverBuilder.ResolutionType resolutionType(LocatedNode node, String key) {
        var type = scalar(node, key);
        try {
            return AttributeResolverBuilder.ResolutionType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown resolutionType '" + type + "' at "
                    + node.line() + ":" + node.col(), e);
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
        return new IllegalArgumentException("Unknown key '" + key + "' in search attributeResolver at "
                + node.line() + ":" + node.col());
    }
}
