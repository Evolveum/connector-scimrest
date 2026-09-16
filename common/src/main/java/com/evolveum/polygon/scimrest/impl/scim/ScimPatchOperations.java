/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.scim;

import com.evolveum.polygon.scimrest.schema.RestAttributeDefinition;
import com.evolveum.polygon.scimrest.schema.RestObjectClassDefinition;
import com.evolveum.polygon.scimrest.schema.ScimAttributeMapping;
import com.unboundid.scim2.common.messages.PatchOpType;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.objects.AttributeDelta;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Translates ConnId {@link AttributeDelta}s into SCIM 2.0 PATCH operations (RFC 7644 section 3.5.2)
 * and renders them into the {@code Operations} JSON body of a {@code PATCH} request.
 *
 * <p>The SCIM SDK's {@code RemoveOperation} cannot represent a standard remove of specific values
 * (it only allows a value on the {@code members} path), so the request body is rendered directly
 * instead of going through the SDK's {@code PatchOperation}/{@code PatchRequest} types.</p>
 */
public final class ScimPatchOperations {

    private static final JsonNodeFactory FACTORY = new JsonNodeFactory();
    private static final String PATCH_OP_SCHEMA = "urn:ietf:params:scim:api:messages:2.0:PatchOp";

    private ScimPatchOperations() {
    }

    /**
     * A single SCIM PATCH operation.
     *
     * @param op    the operation type ({@code add}, {@code replace}, {@code remove})
     * @param path  the SCIM attribute path
     * @param value the value to add/replace/remove, or {@code null} for a remove-all (clear) operation
     */
    public record PatchOp(PatchOpType op, String path, JsonNode value) {
    }

    /**
     * Per-attribute PATCH limitations.
     *
     * @param operations     the SCIM PATCH operations the attribute supports ({@code null} = all)
     * @param maxPerRequest  the maximum number of values of the attribute accepted in a single request
     * @param pinnedValues   values to pin the attribute to ({@code null} = not pinned)
     */
    public record PatchAttrConfig(Set<PatchOpType> operations, int maxPerRequest, List<Object> pinnedValues) {

        /** Unlimited by default; a value is only chunked when the user sets a smaller {@code maxPerRequest}. */
        public static final int UNLIMITED = Integer.MAX_VALUE;

        public static PatchAttrConfig defaults() {
            return new PatchAttrConfig(null, UNLIMITED, null);
        }

        public boolean allows(PatchOpType op) {
            return operations == null || operations.contains(op);
        }

        public boolean isPinned() {
            return pinnedValues != null && !pinnedValues.isEmpty();
        }
    }

    /**
     * Builds the list of SCIM PATCH requests (each a list of operations) from the given deltas.
     *
     * <p>When a single operation would carry more values than the attribute's
     * {@code maxPerRequest} allows, it is split across multiple requests. A {@code replace}
     * overflow is split as a clear ({@code remove} without value) followed by chunked
     * {@code add} requests.</p>
     *
     * @param deltas      the attribute deltas to translate
     * @param objectClass the object class definition (for attribute / SCIM path resolution)
     * @param config      per-attribute limitations keyed by ConnId attribute name ({@code null} = none)
     * @return one or more PATCH requests; empty when there is nothing to do
     * @throws ConnectorException if an attribute has no SCIM mapping or an operation is not allowed
     */
    public static List<List<PatchOp>> build(Collection<AttributeDelta> deltas, RestObjectClassDefinition objectClass,
                                            Map<String, PatchAttrConfig> config) {
        var requests = new ArrayList<List<PatchOp>>();
        var cfg = config != null ? config : Map.<String, PatchAttrConfig>of();
        for (var delta : deltas) {
            var mapping = resolveMapping(objectClass, delta.getName());
            var attrConfig = cfg.getOrDefault(delta.getName(), PatchAttrConfig.defaults());
            for (var op : computeOps(delta, mapping, attrConfig)) {
                var n = Math.max(1, attrConfig.maxPerRequest());
                if (op.op() == PatchOpType.REPLACE && valueCount(op.value()) > n) {
                    requests.add(List.of(new PatchOp(PatchOpType.REMOVE, op.path(), null)));
                    for (var chunk : chunkValues(op.value(), n)) {
                        requests.add(List.of(new PatchOp(PatchOpType.ADD, op.path(), chunk)));
                    }
                } else if (valueCount(op.value()) > n) {
                    for (var chunk : chunkValues(op.value(), n)) {
                        requests.add(List.of(new PatchOp(op.op(), op.path(), chunk)));
                    }
                } else {
                    requests.add(List.of(op));
                }
            }
        }
        return requests;
    }

    /**
     * Renders a list of operations into the JSON body of a SCIM PATCH request.
     *
     * @param ops the operations for a single PATCH request
     * @return the request body with {@code schemas} and {@code Operations}
     */
    public static ObjectNode toPatchBody(List<PatchOp> ops) {
        var body = FACTORY.objectNode();
        var schemas = FACTORY.arrayNode();
        schemas.add(FACTORY.stringNode(PATCH_OP_SCHEMA));
        body.set("schemas", schemas);
        var operations = FACTORY.arrayNode();
        for (var op : ops) {
            var opNode = FACTORY.objectNode();
            opNode.put("op", op.op().getStringValue());
            opNode.put("path", op.path());
            if (op.value() != null) {
                opNode.set("value", op.value());
            }
            operations.add(opNode);
        }
        body.set("Operations", operations);
        return body;
    }

    private static List<PatchOp> computeOps(AttributeDelta delta, ScimAttributeMapping mapping,
                                             PatchAttrConfig attrConfig) {
        var path = mapping.scimPath();
        var ops = new ArrayList<PatchOp>();
        if (attrConfig.isPinned()) {
            ops.add(new PatchOp(PatchOpType.REPLACE, path, toValueNode(mapping.wireValues(attrConfig.pinnedValues()))));
        } else if (delta.getValuesToReplace() != null) {
            var replaceValues = delta.getValuesToReplace();
            if (replaceValues.isEmpty()) {
                ops.add(new PatchOp(PatchOpType.REMOVE, path, null));
            } else {
                ops.add(new PatchOp(PatchOpType.REPLACE, path, toValueNode(mapping.wireValues(replaceValues))));
            }
        } else {
            var toRemove = delta.getValuesToRemove();
            if (toRemove != null) {
                ops.add(new PatchOp(PatchOpType.REMOVE, path,
                        toRemove.isEmpty() ? null : toValueNode(mapping.wireValues(toRemove))));
            }
            var toAdd = delta.getValuesToAdd();
            if (toAdd != null && !toAdd.isEmpty()) {
                ops.add(new PatchOp(PatchOpType.ADD, path, toValueNode(mapping.wireValues(toAdd))));
            }
        }
        for (var op : ops) {
            if (!attrConfig.allows(op.op())) {
                // A deterministic limitation of the configured PATCH support — retries will not
                // change it, so it is a configuration error, not a transient one.
                throw new ConfigurationException("SCIM PATCH operation '" + op.op().getStringValue()
                        + "' is not supported for attribute '" + delta.getName() + "'");
            }
        }
        return ops;
    }

    private static ScimAttributeMapping resolveMapping(RestObjectClassDefinition objectClass, String connIdName) {
        RestAttributeDefinition definition = objectClass.attributeFromConnIdName(connIdName);
        if (definition == null) {
            throw new ConfigurationException("Unknown attribute in SCIM PATCH update: " + connIdName);
        }
        var mapping = definition.scim();
        if (mapping == null) {
            throw new ConfigurationException("Attribute '" + connIdName + "' has no SCIM mapping");
        }
        return mapping;
    }

    private static int valueCount(JsonNode value) {
        if (value == null) {
            return 0;
        }
        return value.isArray() ? value.size() : 1;
    }

    /** Wraps wired values into the SCIM JSON value: a scalar for a single value, an array for several. */
    public static JsonNode toValueNode(List<JsonNode> values) {
        if (values.size() == 1) {
            return values.get(0);
        }
        var array = FACTORY.arrayNode();
        values.forEach(array::add);
        return array;
    }

    private static List<JsonNode> chunkValues(JsonNode value, int n) {
        var chunks = new ArrayList<JsonNode>();
        if (value != null && value.isArray()) {
            for (int i = 0; i < value.size(); i += n) {
                var end = Math.min(n, value.size() - i);
                var chunk = FACTORY.arrayNode();
                for (int j = 0; j < end; j++) {
                    chunk.add(value.get(i + j));
                }
                chunks.add(chunk);
            }
        } else if (value != null) {
            chunks.add(value);
        }
        return chunks;
    }
}
