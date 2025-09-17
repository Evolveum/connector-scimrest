package com.evolveum.polygon.scimrest.groovy.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class ConnectorManifest {
    private final JsonNode json;

    public ConnectorManifest(InputStream resource) {
        try {
            if (resource == null) {
                json = JsonNodeFactory.instance.objectNode();
            } else {
                json = new ObjectMapper().readTree(resource);
            }
        } catch (IOException e) {
            throw new RuntimeException("Can not read connector manifest", e);
        } finally {
            //resource.close();
        }
    }

    ObjectNode connector() {
        return require(ObjectNode.class, json.get("connector"), "connector object not present");
    }

    List<String> scripts(String type) {

        var schemaScripts = connector().get(type);
        var ret = new ArrayList<String>(schemaScripts.size());
        if (schemaScripts == null || schemaScripts.isEmpty()) {
            return ret;
        }
        for  (JsonNode schema : schemaScripts) {
            ret.add(schema.get("script").asText());
        }
        return ret;
    }

    public List<String> schemaScripts() {
        return scripts("schema");
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
