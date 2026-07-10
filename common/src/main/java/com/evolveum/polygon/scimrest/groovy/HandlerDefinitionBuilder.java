/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy;

import com.evolveum.polygon.conndev.yaml.ScriptResources;
import com.evolveum.polygon.scimrest.yaml.YamlRestHandlerLoader;
import com.evolveum.polygon.scimrest.yaml.model.YamlOperationDocument;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Handler definition loader dispatching between the two operation front-ends: Groovy scripts go to
 * {@link GroovyRestHandlerBuilder}, YAML documents ({@code *.yaml}/{@code *.yml}) go to
 * {@link YamlRestHandlerLoader} — both drive the same {@code RestHandlerBuilder}, so operations and
 * authentication may be defined in either format, file by file. A referenced Groovy script missing
 * from the bundle falls back to the YAML document of the same name ({@link ScriptResources}).
 */
public class HandlerDefinitionBuilder extends GroovyRestHandlerBuilder {

    private final YamlRestHandlerLoader yamlLoader;

    public HandlerDefinitionBuilder(GroovyContext context, ConnectorContext schema) {
        super(context, schema);
        this.yamlLoader = new YamlRestHandlerLoader(this, context);
    }

    @Override
    public void loadFromResource(String resource) {
        var resolved = ScriptResources.resolveWithYamlFallback(getClass(), resource);
        if (ScriptResources.isYaml(resolved)) {
            loadYamlFromResource(resolved);
        } else {
            super.loadFromResource(resolved);
        }
    }

    private void loadYamlFromResource(String resource) {
        var stream = getClass().getResourceAsStream(resource);
        if (stream == null) {
            throw new IllegalArgumentException("YAML operations resource not found: " + resource);
        }
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            yamlLoader.load(reader, resource);
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't read YAML operations document " + resource, e);
        }
    }

    /** Operations documents parsed from YAML definitions; empty when the connector has none. */
    public List<YamlOperationDocument> yamlOperations() {
        return yamlLoader.documents();
    }
}
