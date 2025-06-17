package com.evolveum.polygon.scim.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public interface JsonAttributeMapping extends AttributeMapping<Object, JsonNode> {


    @Override
    default List<Object> toConnIdValues(Iterable<JsonNode> wireValues) {
        List<Object> ret;
        if (wireValues instanceof ArrayNode array) {
            ret = new ArrayList<>(array.size());
        } else if (wireValues instanceof JsonNode jsonNode) {
            // All JSON Nodes are iterable, but actually they return empty set if they are value nodes
            return List.of(toConnIdValue(jsonNode));
        } else {
            ret = new ArrayList<>();
        }
        wireValues.forEach(ret::add);
        return ret;
    }
}
