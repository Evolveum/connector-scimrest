/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy;

import com.evolveum.polygon.conndev.yaml.ScriptResources;
import com.evolveum.polygon.scimrest.yaml.YamlOperationLoader;
import com.evolveum.polygon.scimrest.yaml.model.YamlOperationDocument;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Handler definition loader dispatching between the two operation front-ends: Groovy scripts go to
 * {@link GroovyRestHandlerBuilder} (the functional handlers), YAML documents ({@code *.yaml}/
 * {@code *.yml}) go to {@link YamlOperationLoader} and are only parsed into the typed model.
 * A referenced Groovy script missing from the bundle falls back to the YAML document of the same
 * name ({@link ScriptResources}).
 * <p>
 * The YAML documents are inert for now: the connector still runs solely on the Groovy-built
 * handlers. They exist so YAML operation definitions are parsed and validated, and can later be
 * bound onto the same {@code RestHandlerBuilder} the Groovy DSL drives (see
 * {@code .tasks/in-progress/yaml-operations-inert-loading.txt}).
 */
public class HandlerDefinitionBuilder extends GroovyRestHandlerBuilder {

    private final YamlOperationLoader yamlLoader = new YamlOperationLoader();

    public HandlerDefinitionBuilder(GroovyContext context, ConnectorContext schema) {
        super(context, schema);
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
