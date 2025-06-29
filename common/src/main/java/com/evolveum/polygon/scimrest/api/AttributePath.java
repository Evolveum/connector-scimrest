/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Implementation of SCIM-compatible attribute path for navigating and retrieving nodes from JSON
 *
 * @param components
 */
public record AttributePath(List<Component> components) implements Resolver<ObjectNode, JsonNode> {


    public static AttributePath of(Component... components) {
        return new AttributePath(List.of(components));
    }

    public static AttributePath of(String... components) {
        return new AttributePath(List.of(Arrays.stream(components).map(Attribute::new).toArray(Component[]::new)));
    }

    @Override
    public JsonNode resolve(ObjectNode node) {
        JsonNode current = node;
        for (Component component : components) {
            current = component.resolve(current);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    public AttributePath child(String name) {
        return child( new Attribute(name));
    }

    public AttributePath valueFilter(String key, Object value) {
        return child(new SimpleValueFilter(Map.of(key, value)));
    }

    public AttributePath firstValue() {
        return child(new IndexFilter(0));
    }

    public AttributePath withoutFilters() {
        return new AttributePath(components.stream().filter(i -> !(i instanceof FilterComponent)).toList());
    }

    private AttributePath child(Component attribute) {
        var ret = new ArrayList<>(this.components);
        ret.add(attribute);
        return new AttributePath(List.copyOf(ret));
    }

    public Attribute onlyAttribute() {
        if (components.size() == 1 && components.get(0) instanceof Attribute attribute) {
            return attribute;
        }
        return null;
    }

    public record Attribute(String name) implements Component {

        @Override
        public JsonNode resolve(JsonNode contextNode) {
            if (contextNode instanceof ObjectNode object) {
                return object.get(name);
            }
            return null;
        }

        @Override
        public void toString(StringBuilder builder, Component previous) {
            if (previous instanceof Attribute) {
                builder.append('.');
            }
            builder.append(name);
        }
    }

    public record Extension(String name) implements Component {

        @Override
        public JsonNode resolve(JsonNode contextNode) {
            if (contextNode instanceof ObjectNode object) {
                return object.get(name);
            }
            return null;
        }

        @Override
        public void toString(StringBuilder builder, Component previous) {
            if (previous instanceof Attribute) {
                builder.append('.');
            }
            builder.append(name);
        }
    }

    public record IndexFilter(int index) implements FilterComponent {

        @Override
        public JsonNode resolve(JsonNode contextNode) {
            if (contextNode instanceof ArrayNode array) {
                if (array.size() > index) {
                    return array.get(index);
                }
                return null;
            }
            return null;
        }

        @Override
        public void toString(StringBuilder builder, Component previous) {
            builder.append('[').append(index).append(']');
        }
    }

    public record SimpleValueFilter(Map<String, Object> keyValues) implements FilterComponent {

        @Override
        public JsonNode resolve(JsonNode contextNode) {
            if (contextNode instanceof ObjectNode node && matches(node)) {
                return node;
            }
            if (contextNode instanceof ArrayNode array) {
                for (JsonNode node : array) {
                    if (matches(node)) {
                        return node;
                    }
                }
            }
            return null;
        }

        private boolean matches(JsonNode node) {
            if (node instanceof ObjectNode objectNode) {
                for (var keyValue : keyValues.entrySet()) {
                    var maybeVal = objectNode.get(keyValue.getKey());
                    if (maybeVal instanceof TextNode) {
                        return keyValue.getValue().equals(maybeVal.asText());
                    }
                    if (maybeVal instanceof NumericNode) {
                        return keyValue.getValue().equals(maybeVal.numberValue());
                    }
                    if (maybeVal instanceof BooleanNode) {
                        return keyValue.getValue().equals(maybeVal.asBoolean());
                    }
                    return false;
                }
            }
            return false;
        }

        @Override
        public void toString(StringBuilder builder, Component previous) {
            builder.append("[");
            boolean first = true;
            for(var entry : keyValues.entrySet()) {
                if (!first) {
                    builder.append(" and ");
                }
                first = false;
                builder.append(entry.getKey());
                builder.append(" = ");
                if (entry.getValue() instanceof String) {
                    builder.append('"').append(entry.getValue()).append('"');
                } else {
                    builder.append(entry.getValue());
                }
            }
            builder.append("]");
        }
    }

    public interface Component extends Resolver<JsonNode, JsonNode> {
        void toString(StringBuilder builder, Component previous);
    }

    public interface FilterComponent extends Component {
    }

    @Override
    public String toString() {
        var ret = new StringBuilder();
        Component previous = null;
        Component current = null;

        var it =  components.iterator();
        while(it.hasNext()) {
            previous = current;
            current = it.next();
            current.toString(ret, previous);
        }
        return ret.toString();
    }
}
