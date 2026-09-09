/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.yaml;

import com.evolveum.polygon.conndev.yaml.GroovyScriptCompiler;
import com.evolveum.polygon.conndev.yaml.binding.LocatedDocument;
import com.evolveum.polygon.conndev.yaml.binding.LocatedNode;
import com.evolveum.polygon.conndev.yaml.binding.YamlBinder;
import com.evolveum.polygon.scimrest.groovy.handler.RestHandlerBuilder;

import java.io.Reader;

/**
 * The location-aware, engine-driven front-end for REST/SCIM operation and authentication documents —
 * the YAML counterpart of the Groovy DSL, but driving the live builders through the {@code @Yaml.*}
 * binding engine (see {@link YamlBinder}) instead of a dedicated POJO model.
 *
 * <p>The document uses the extended envelope: an {@code objectClasses} mapping (object-class name to
 * its {@code create}/{@code update}/{@code delete}/{@code search} blocks) and/or a top-level
 * {@code authentication} block. Each block binds onto the corresponding live operation builder, so
 * the YAML operations execute exactly like their Groovy counterparts and carry a precise
 * {@code source:line:col} on every value the builders record as a {@code DefinitionValue}.
 */
public final class YamlRestOperationsLoader {

    private final RestHandlerBuilder builder;
    private final GroovyScriptCompiler compiler;

    public YamlRestOperationsLoader(RestHandlerBuilder builder, GroovyScriptCompiler compiler) {
        this.builder = builder;
        this.compiler = compiler;
    }

    public void load(Reader reader, String sourceName) {
        load(LocatedDocument.parse(sourceName, reader));
    }

    public void load(LocatedDocument document) {
        var binder = new YamlBinder(document, compiler);
        var root = document.root();
        if (root.kind() != LocatedNode.Kind.OBJECT) {
            throw new IllegalArgumentException("YAML operations document must be a mapping ("
                    + document.sourceName() + ")");
        }
        for (var entry : root.entries()) {
            switch (entry.key()) {
                case "objectClasses" -> applyObjectClasses(binder, entry.value());
                case "authentication" -> applyAuthentication(binder, entry.value());
                default -> throw unknownTopLevelKey(document, entry);
            }
        }
    }

    private void applyObjectClasses(YamlBinder binder, LocatedNode node) {
        for (var entry : requireMap(node, "objectClasses").entries()) {
            binder.bind(entry.value(), builder.objectClass(entry.key()));
        }
    }

    private void applyAuthentication(YamlBinder binder, LocatedNode node) {
        binder.bind(requireMap(node, "authentication"), builder.authentication());
    }

    private static LocatedNode requireMap(LocatedNode node, String key) {
        if (node == null || node.isNull()) {
            return node;
        }
        if (node.kind() != LocatedNode.Kind.OBJECT) {
            throw new IllegalArgumentException("'" + key + "' must be a mapping but found a " + node.kind()
                    + " at " + node.line() + ":" + node.col());
        }
        return node;
    }

    private static IllegalArgumentException unknownTopLevelKey(LocatedDocument document, LocatedNode.Entry entry) {
        return new IllegalArgumentException("Unknown top-level key '" + entry.key() + "' in YAML operations document ("
                + document.sourceName() + ":" + entry.keyLine() + ":" + entry.keyCol() + ")");
    }
}
