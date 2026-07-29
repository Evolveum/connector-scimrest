/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.schema.BaseSchema;
import com.evolveum.polygon.conndev.schema.BaseSchemaBuilder;
import com.evolveum.polygon.conndev.yaml.ScriptResources;
import com.evolveum.polygon.conndev.yaml.YamlSchemaLoader;
import com.evolveum.polygon.scimrest.schema.RestSchemaBuilderImpl;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Schema definition loader dispatching between the two schema front-ends: Groovy definitions go to
 * {@link GroovySchemaLoader} (the functional {@code RestSchema}), YAML definitions
 * ({@code *.schema.yaml}/{@code *.schema.yml}) go to the conndev {@link YamlSchemaLoader} and build
 * a conndev {@link BaseSchema}. A referenced Groovy definition missing from the bundle falls back
 * to the YAML document of the same name ({@link ScriptResources}).
 * <p>
 * The YAML-built schema is inert for now: nothing is derived from it, the connector still runs
 * solely on the Groovy/SCIM-discovery schema. It exists so YAML definitions are parsed and
 * validated, and so the declarative model can later be compared with the functional one and
 * gradually take over (see {@code .tasks/in-progress/yaml-declarative-schema-inert.txt}).
 */
public class SchemaDefinitionLoader extends GroovySchemaLoader {

    private final YamlSchemaLoader yamlLoader;
    private boolean yamlLoaded;

    public SchemaDefinitionLoader(GroovyContext context, RestSchemaBuilderImpl schemaBuilder) {
        super(context, schemaBuilder);
        // The declarative YAML schema must be fully literal — its builder deliberately gets no
        // runtime context, so any context-dependent construct fails fast during loading.
        this.yamlLoader = new YamlSchemaLoader(
                new BaseSchemaBuilder(schemaBuilder.connectorClass(), ContextLookup.none()));
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
            throw new IllegalArgumentException("YAML schema definition resource not found: " + resource);
        }
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            yamlLoader.load(reader, resource);
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't read YAML schema definition " + resource, e);
        }
        yamlLoaded = true;
    }

    /** Conndev schema built from the YAML definitions; null when no YAML definition was loaded. */
    public BaseSchema baseSchema() {
        return yamlLoaded ? yamlLoader.build() : null;
    }
}
