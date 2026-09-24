/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.yaml.binding;

import com.evolveum.polygon.conndev.api.AttributePathDeclaration;
import com.evolveum.polygon.conndev.api.BasicJsonPathFormat;
import com.evolveum.polygon.conndev.yaml.decl.DeclPathValueParser;
import com.evolveum.polygon.conndev.yaml.decl.GroovySyntaxChecker;
import com.evolveum.polygon.conndev.yaml.decl.LocatedNode;
import com.evolveum.polygon.conndev.yaml.decl.CustomYamlHandler;
import com.evolveum.polygon.conndev.yaml.decl.DeclYamlBinder;
import com.evolveum.polygon.scimrest.groovy.api.RestSearchEndpointBuilder;

/**
 * Binds a search endpoint's {@code objectExtractor:} key. Two shapes are accepted:
 *
 * <ul>
 *   <li>a {@code {type, value}} mapping — a declarative attribute path (JSON Pointer or basic
 *       JSONPath, the same formats as the attribute {@code path} keys); the type defaults to
 *       JSON_PATH. This yields a path-based object extractor without any Groovy.</li>
 *   <li>a string / block scalar — the Groovy form (a closure extracting the list from
 *       {@code response}).</li>
 * </ul>
 */
public class ObjectExtractorHandler implements CustomYamlHandler {

    @Override
    public void apply(DeclYamlBinder binder, Object target, LocatedNode value) {
        if (!(target instanceof RestSearchEndpointBuilder endpoint)) {
            throw new IllegalArgumentException("The 'objectExtractor' key requires a search endpoint, got: "
                    + target.getClass().getName());
        }
        if (value == null || value.isNull()) {
            return; // no extractor declared — the default (array or single object) stays
        }
        if (value.kind() == LocatedNode.Kind.OBJECT) {
            var location = binder.document().location(value.line(), value.col());
            var declaration = (AttributePathDeclaration<?, ?>) new DeclPathValueParser(BasicJsonPathFormat.INSTANCE)
                    .coerce(value, location, AttributePathDeclaration.class);
            endpoint.objectExtractor(declaration);
            return;
        }
        if (value.isValue()) {
            endpoint.objectExtractor(binder.compileClosure(value.text()));
            return;
        }
        throw new IllegalArgumentException("The 'objectExtractor' key requires a path mapping ({type, value})"
                + " or a Groovy block at " + value.line() + ":" + value.col());
    }

    @Override
    public void checkGroovySyntax(LocatedNode value, String path, GroovySyntaxChecker checker) {
        // Only the string form carries Groovy; the {type, value} mapping is checked when the
        // path is parsed at build time.
        if (value != null && value.kind() == LocatedNode.Kind.SCALAR) {
            checker.checkFragment(value, path);
        }
    }
}
