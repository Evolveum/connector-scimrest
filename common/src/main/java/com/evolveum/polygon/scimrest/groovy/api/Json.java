package com.evolveum.polygon.scimrest.groovy.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

public class Json {

    public static List<JsonNode> toList(JsonNode json) {
        if (json instanceof ArrayNode array) {
            var ret = new ArrayList<JsonNode>();
            array.elements().forEachRemaining(ret::add);
            return ret;
        }
        return List.of(json);
    }
}
