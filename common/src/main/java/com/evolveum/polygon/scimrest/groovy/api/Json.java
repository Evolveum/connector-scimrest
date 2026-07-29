package com.evolveum.polygon.scimrest.groovy.api;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

public class Json {

    public static List<JsonNode> toList(JsonNode json) {
        if (json instanceof ArrayNode array) {
            var ret = new ArrayList<JsonNode>();
            array.elements().forEach(ret::add);
            return ret;
        }
        return List.of(json);
    }
}
