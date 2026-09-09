/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.yaml;

import com.evolveum.polygon.conndev.groovy.GroovyContext;
import com.evolveum.polygon.conndev.yaml.GroovyScriptCompiler;
import com.evolveum.polygon.scimrest.groovy.handler.RestHandlerBuilder;

import java.io.Reader;
import java.io.StringReader;

/**
 * YAML front-end of connector operation/authentication scripts — the YAML counterpart of
 * {@link com.evolveum.polygon.scimrest.groovy.GroovyRestHandlerBuilder}. Documents use the extended
 * envelope (a top-level {@code objectClasses} mapping and/or an {@code authentication} block) and are
 * driven through the location-aware binding engine ({@link YamlRestOperationsLoader}) onto the same
 * format-agnostic {@link RestHandlerBuilder} the Groovy DSL drives.
 */
public class YamlRestHandlerLoader {

    private final YamlRestOperationsLoader operationsLoader;

    public YamlRestHandlerLoader(RestHandlerBuilder builder, GroovyContext groovyContext) {
        var scriptCompiler = new GroovyScriptCompiler(groovyContext);
        this.operationsLoader = new YamlRestOperationsLoader(builder, scriptCompiler);
    }

    public void loadFromString(String yaml) {
        operationsLoader.load(new StringReader(yaml), "inline document");
    }

    public void load(Reader reader, String sourceName) {
        operationsLoader.load(reader, sourceName);
    }
}
