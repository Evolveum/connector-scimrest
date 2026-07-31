/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.scimrest.groovy.impl;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import java.util.List;

public class ConnectorManifest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ObjectMapper YAML = new YAMLMapper();

    private final JsonNode json;

    public ConnectorManifest(InputStream resource) {
        try {
            if (resource == null) {
                json = JsonNodeFactory.instance.objectNode();
            } else {
                json = JSON.readTree(resource);
            }
        } catch (JacksonException e) {
            throw new ConfigurationException("Failed to read connector manifest", e);
        } finally {
            //resource.close();
        }
    }

    private ConnectorManifest(JsonNode json) {
        this.json = json;
    }

    /**
     * Loads the manifest bundled under {@code baseName} — written either in YAML
     * ({@code .yaml}/{@code .yml}) or JSON ({@code .json}), with the same structure. Exactly one
     * format may be bundled (two formats are two sources of truth — a packaging error); a missing
     * manifest yields the empty one, as before.
     */
    public static ConnectorManifest load(Class<?> anchor, String baseName) {
        var present = List.of(baseName + ".yaml", baseName + ".yml", baseName + ".json").stream()
                .filter(candidate -> anchor.getResource(candidate) != null)
                .toList();
        if (present.size() > 1) {
            throw new ConfigurationException(
                    "Multiple connector manifests found: " + String.join(", ", present) + " — bundle exactly one");
        }
        if (present.isEmpty()) {
            return new ConnectorManifest((InputStream) null);
        }

        var resource = present.get(0);
        try (var stream = anchor.getResourceAsStream(resource)) {
            var mapper = resource.endsWith(".json") ? JSON : YAML;
            return new ConnectorManifest(mapper.readTree(stream));
        } catch (IOException e) {
            throw new ConfigurationException("Failed to read connector manifest " + resource, e);
        }
    }

    ObjectNode connector() {
        return require(ObjectNode.class, json.get("connector"), "connector object not present");
    }

    List<String> scripts(String type) {

        var schemaScripts = connector().get(type);
        if (schemaScripts == null || schemaScripts.isEmpty()) {
            return new ArrayList<>();
        }
        var ret = new ArrayList<String>(schemaScripts.size());
        for  (JsonNode schema : schemaScripts) {
            ret.add(schema.get("script").asText());
        }
        return ret;
    }

    public List<String> schemaScripts() {
        return scripts("schema");
    }

    public List<String> authorizationScripts() {
        return scripts("authorization");
    }

    public List<String> operationScripts() {
        return scripts("operation");
    }


    private <T extends JsonNode> T require(Class<T> clazz, JsonNode node, String message) {
        if (node == null || !clazz.isInstance(node)) {
            throw new IllegalArgumentException(message);
        }
        return clazz.cast(node);
    }
}
