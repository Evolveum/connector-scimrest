/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy.schema;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.groovy.GroovyContext;
import com.evolveum.polygon.conndev.groovy.GroovySchemaLoader;
import com.evolveum.polygon.conndev.schema.BaseSchema;
import com.evolveum.polygon.conndev.yaml.ScriptResources;
import com.evolveum.polygon.conndev.yaml.YamlSchemaLoader;
import com.evolveum.polygon.scimrest.schema.RestSchema;
import com.evolveum.polygon.scimrest.schema.RestSchemaBuilderImpl;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Schema definition loader dispatching between the two schema front-ends: Groovy definitions go to
 * {@link GroovySchemaLoader} (the functional {@code RestSchema}), YAML definitions
 * ({@code *.schema.yaml}/{@code *.schema.yml}) go to the conndev {@link YamlSchemaLoader} and build
 * a {@link RestSchema}. A referenced Groovy definition missing from the bundle falls back to the
 * YAML document of the same name ({@link ScriptResources}).
 * <p>
 * The YAML front-end drives a live {@link RestSchemaBuilderImpl} (see
 * {@link #baseSchema()}), so the declarative definitions populate a real {@code RestSchema} — SCIM
 * mappings and {@code DefinitionValue} source locations included — rather than an inert conndev
 * {@link BaseSchema}. It is deliberately built without a runtime context so any context-dependent
 * construct fails fast during loading.
 */
public class SchemaDefinitionLoader extends GroovySchemaLoader {

    private final YamlSchemaLoader yamlLoader;
    private boolean yamlLoaded;

    public SchemaDefinitionLoader(GroovyContext context, RestSchemaBuilderImpl schemaBuilder) {
        super(context, schemaBuilder);
        // The declarative YAML schema must be fully literal — its builder deliberately gets no
        // runtime context, so any context-dependent construct fails fast during loading. It is a
        // live RestSchemaBuilderImpl (not the inert conndev BaseSchemaBuilder), so the definitions
        // populate a real RestSchema with SCIM mappings and source locations.
        this.yamlLoader = new YamlSchemaLoader(
                new RestSchemaBuilderImpl(schemaBuilder.connectorClass(), ContextLookup.none()));
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
            // The bundle is missing a schema definition the connector expects — a
            // packaging/configuration error, not a caller-input error.
            throw new ConfigurationException("YAML schema definition resource not found: " + resource);
        }
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            yamlLoader.load(reader, resource);
        } catch (IOException e) {
            throw new ConfigurationException("Couldn't read YAML schema definition " + resource + ": " + e.getMessage(), e);
        }
        yamlLoaded = true;
    }

    /** Conndev schema built from the YAML definitions; null when no YAML definition was loaded. */
    public BaseSchema baseSchema() {
        return yamlLoaded ? yamlLoader.build() : null;
    }

    @Override
    public RestSchema build() {
        return (RestSchema) super.build();
    }
}
