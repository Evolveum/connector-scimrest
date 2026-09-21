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
 * {@link GroovySchemaLoader} directly. YAML definitions ({@code *.schema.yaml}/{@code
 * *.schema.yml}) are parsed by the conndev {@link YamlSchemaLoader} onto their own throwaway,
 * deliberately context-less builder - so any context-dependent construct fails fast during loading,
 * the same guarantee Groovy validation already has - and merged into the shared builder immediately,
 * before {@link #loadFromResource} returns. A referenced Groovy definition missing from the bundle
 * falls back to the YAML document of the same name ({@link ScriptResources}).
 * <p>
 * Both branches are symmetric from the caller's side: one {@link #loadFromResource} call per
 * resource is enough regardless of format, exactly as if every script had been Groovy - there is no
 * separate step to remember afterward.
 */
public class SchemaDefinitionLoader extends GroovySchemaLoader {

    // GroovySchemaLoader's own schemaBuilder field is package-private (conndev's package), so it's
    // not visible here even though we extend it - keep our own reference to the same instance
    // instead, for merging YAML definitions into it below.
    private final RestSchemaBuilderImpl schemaBuilder;

    public SchemaDefinitionLoader(GroovyContext context, RestSchemaBuilderImpl schemaBuilder) {
        super(context, schemaBuilder);
        this.schemaBuilder = schemaBuilder;
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
        // A fresh, throwaway builder per file - deliberately built without a runtime context, so
        // any context-dependent construct fails fast during loading. It's a live
        // RestSchemaBuilderImpl (not the inert conndev BaseSchemaBuilder), so the definitions carry
        // the same real SCIM/REST attribute mappings a Groovy-declared object class would.
        var yamlBuilder = new RestSchemaBuilderImpl(schemaBuilder.connectorClass(), ContextLookup.none());
        var yamlLoader = new YamlSchemaLoader(yamlBuilder);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            yamlLoader.load(reader, resource);
        } catch (IOException e) {
            throw new ConfigurationException("Couldn't read YAML schema definition " + resource + ": " + e.getMessage(), e);
        }
        RestSchema parsed = (RestSchema) yamlLoader.build();
        for (var definition : parsed.objectClasses()) {
            schemaBuilder.defineObjectClass(definition);
        }
    }

    @Override
    public RestSchema build() {
        return (RestSchema) super.build();
    }
}
