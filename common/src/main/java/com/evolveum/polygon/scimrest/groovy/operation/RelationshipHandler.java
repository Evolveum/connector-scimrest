/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 */
package com.evolveum.polygon.scimrest.groovy.operation;

import com.evolveum.polygon.conndev.api.AttributePath;
import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.build.api.UpdateOperationBuilder.UpdateRequest;
import com.evolveum.polygon.conndev.json.JsonAttributeMapping;
import com.evolveum.polygon.conndev.spi.UpdateOperationHandler;
import com.evolveum.polygon.scimrest.JacksonBodyHandler;
import com.evolveum.polygon.scimrest.api.HttpRequestSpecification;
import com.evolveum.polygon.scimrest.groovy.api.HttpMethod;
import com.evolveum.polygon.scimrest.groovy.connector.RestConnectorContext;
import com.evolveum.polygon.scimrest.impl.rest.ErrorDetail;
import com.evolveum.polygon.scimrest.impl.rest.HttpExceptionMapper;
import com.evolveum.polygon.scimrest.impl.rest.HttpStatusMapper;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeDelta;
import org.identityconnectors.framework.common.objects.AttributeDeltaBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObjectReference;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
Reconciles one attribute against separate REST records. The complete snapshot and mutation plan are
validated before writing; failures after a write propagate without claiming an atomic rollback.
*/
final class RelationshipHandler implements UpdateOperationHandler {

    private static final JsonNodeFactory FACTORY = JsonNodeFactory.instance;

    private final RestConnectorContext context;
    private final String attribute;
    private final String endpoint;
    private final String deleteEndpoint;
    private final AttributePath sourceField;
    private final AttributePath targetField;
    private final AttributePath idField;
    private final AttributePath recordsPath;
    private final String sourceParameter;
    private final String sourceType;
    private final String targetType;
    private final int pageSize;
    private final String offsetParameter;
    private final String limitParameter;
    private final int initialOffset;
    private final int maxPages;
    private final Object[] locks = new Object[64];

    RelationshipHandler(RestConnectorContext context, String attribute, String endpoint, String deleteEndpoint, AttributePath sourceField,
            AttributePath targetField, AttributePath idField, AttributePath recordsPath, String sourceParameter, String sourceType, String targetType,
            int pageSize, String offsetParameter, String limitParameter, int initialOffset, int maxPages) {
        this.context = context;
        this.attribute = attribute;
        this.endpoint = endpoint;
        this.deleteEndpoint = deleteEndpoint;
        this.sourceField = sourceField;
        this.targetField = targetField;
        this.idField = idField;
        this.recordsPath = recordsPath;
        this.sourceParameter = sourceParameter;
        this.sourceType = sourceType;
        this.targetType = targetType;
        this.pageSize = pageSize;
        this.offsetParameter = offsetParameter;
        this.limitParameter = limitParameter;
        this.initialOffset = initialOffset;
        this.maxPages = maxPages;
        for (int index = 0; index < locks.length; index++) {
            locks[index] = new Object();
        }
    }

    @Override
    public boolean requiresOriginalState() {
        return false;
    }

    @Override
    public Capability<AttributeDelta, UpdateOperationHandler> canHandle(Collection<AttributeDelta> deltas, OperationOptions options) {
        return new Capability<>(this, deltas.stream().filter(delta -> delta.is(attribute)).toList());
    }

    @Override
    public UpdateResponse update(UpdateRequest request, OperationOptions options, ContextLookup operationContext) {
        if (request.attributeDeltaSet().size() != 1) {
            throw new InvalidAttributeValueException("Expected one relationship delta for " + attribute);
        }
        var source = identifier(request.uid().getUidValue(), sourceType);
        var applied = appliedDelta(request.attributeDeltaSet().iterator().next());
        var delta = normalize(applied);
        // Serialize updates for the same source within this handler, with bounded lock storage.
        synchronized (locks[Math.floorMod(source.hashCode(), locks.length)]) {
            reconcile(source, delta);
        }
        return new UpdateResponse(request.uid(), Set.of(applied));
    }

    /**
    Prepares canonical applied identifiers before I/O, preserving input types and reference metadata.
    */
    private AttributeDelta appliedDelta(AttributeDelta delta) {
        if (!"integer".equals(targetType)) {
            return delta;
        }
        var builder = new AttributeDeltaBuilder().setName(delta.getName());
        if (delta.getValuesToReplace() != null) {
            builder.addValueToReplace(appliedValues(delta.getValuesToReplace()));
        } else {
            if (delta.getValuesToAdd() != null) {
                builder.addValueToAdd(appliedValues(delta.getValuesToAdd()));
            }
            if (delta.getValuesToRemove() != null) {
                builder.addValueToRemove(appliedValues(delta.getValuesToRemove()));
            }
        }
        return builder.build();
    }

    private List<Object> appliedValues(List<Object> values) {
        var applied = new LinkedHashMap<String, Object>();
        for (var value : values) {
            var target = identifier(value, targetType);
            applied.putIfAbsent(target, appliedValue(value, target));
        }
        return new ArrayList<>(applied.values());
    }

    private Object appliedValue(Object value, String target) {
        if (value instanceof String) {
            return target;
        }
        if (value instanceof ConnectorObjectReference reference) {
            var referenced = reference.getValue();
            var original = referenced.getAttributeByName(Uid.NAME);
            if (target.equals(original.getValue().getFirst())) {
                return reference;
            }
            var uid = original instanceof Uid previous ? new Uid(target, previous.getRevision(), previous.getNameHint()) : new Uid(target);
            var builder = new ConnectorObjectBuilder().setObjectClass(referenced.getObjectClass());
            for (var field : referenced.getAttributes()) {
                if (!field.is(Uid.NAME)) {
                    builder.addAttribute(field);
                }
            }
            builder.setUid(uid);
            return new ConnectorObjectReference(reference.hasObject() ? builder.build() : builder.buildIdentification());
        }
        return value;
    }

    private AttributeDelta normalize(AttributeDelta delta) {
        var builder = new AttributeDeltaBuilder().setName(attribute);
        if (delta.getValuesToReplace() != null) {
            builder.addValueToReplace(identifiers(delta.getValuesToReplace()));
        } else {
            builder.addValueToAdd(identifiers(delta.getValuesToAdd()));
            builder.addValueToRemove(identifiers(delta.getValuesToRemove()));
        }
        return builder.build();
    }

    private List<String> identifiers(List<Object> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().map(value -> identifier(value, targetType)).distinct().toList();
    }

    private void reconcile(String source, AttributeDelta delta) {
        var records = readRecords(source);
        var current = AttributeBuilder.build(attribute, records.keySet());
        var desired = new LinkedHashSet<>(delta.applyTo(current).getValue());
        var deletions = new ArrayList<HttpRequestSpecification>();
        var creations = new ArrayList<HttpRequestSpecification>();
        for (var entry : records.entrySet()) {
            if (!desired.contains(entry.getKey())) {
                for (var recordId : entry.getValue()) {
                    deletions.add(request(HttpMethod.DELETE, deleteEndpoint, source).pathParameter("id", encodeSegment(recordId)));
                }
            }
        }
        for (var value : desired) {
            var target = (String) value;
            if (!records.containsKey(target)) {
                var body = FACTORY.objectNode();
                putField(body, sourceField, wireValue(source, sourceType));
                putField(body, targetField, wireValue(target, targetType));
                creations.add(request(HttpMethod.POST, endpoint, source).header("Content-Type", "application/json")
                        .body(body.toString().getBytes(StandardCharsets.UTF_8)));
            }
        }
        for (var deletion : deletions) {
            mutate(deletion, HttpStatusMapper.OperationKind.DELETE);
        }
        for (var creation : creations) {
            mutate(creation, HttpStatusMapper.OperationKind.CREATE);
        }
    }

    private Map<String, List<String>> readRecords(String source) {
        var records = new LinkedHashMap<String, List<String>>();
        var recordIds = new HashSet<String>();
        long offset = initialOffset;
        for (int page = 0; page < maxPages; page++) {
            var request = request(HttpMethod.GET, endpoint, source);
            if (sourceParameter != null) {
                request.queryParameter(sourceParameter, source);
            }
            if (pageSize > 0) {
                request.queryParameter(offsetParameter, offset).queryParameter(limitParameter, pageSize);
            }
            var response = execute(request, new JacksonBodyHandler<>(JsonNode.class, "relationship " + attribute + ", page " + (page + 1)));
            checkStatus(response, HttpStatusMapper.OperationKind.SEARCH);
            if (response.statusCode() != 200 || !(response.body() instanceof JsonNode body)) {
                throw invalidSnapshot("expected a completed HTTP 200 collection response");
            }
            var collection = recordsPath == null ? body : recordsPath.resolve(body, JsonAttributeMapping.NULLABLE_PATH_RESOLVER);
            if (collection == null || !collection.isArray()) {
                throw invalidSnapshot("recordsPath must resolve to an array");
            }
            for (var record : collection) {
                if (!record.isObject()) {
                    throw invalidSnapshot("membership record must be an object");
                }
                var recordId = readIdentifier(record, idField, "string");
                if (!recordIds.add(recordId)) {
                    throw invalidSnapshot("repeated membership-record ID; verify pagination and record identity");
                }
                var owner = readIdentifier(record, sourceField, sourceType);
                if (source.equals(owner)) {
                    var target = readIdentifier(record, targetField, targetType);
                    records.computeIfAbsent(target, ignored -> new ArrayList<>()).add(recordId);
                }
            }
            if (pageSize == 0 || collection.isEmpty()) {
                return records;
            }
            // Servers may cap page size; advance past all returned records, including other sources.
            offset += collection.size();
        }
        throw invalidSnapshot("maxPages reached before an empty page; no relationship changes were sent");
    }

    private String readIdentifier(JsonNode record, AttributePath path, String type) {
        var value = path.resolve(record, JsonAttributeMapping.NULLABLE_PATH_RESOLVER);
        if (value == null || !(value.isString() || value.isIntegralNumber())) {
            throw invalidSnapshot("missing or invalid identifier at " + path);
        }
        try {
            return identifier(value.asString(), type);
        } catch (InvalidAttributeValueException exception) {
            throw invalidSnapshot("invalid identifier at " + path);
        }
    }

    private String identifier(Object value, String type) {
        if (value instanceof ConnectorObjectReference reference) {
            var identification = reference.getValue();
            var uid = identification != null ? identification.getAttributeByName(Uid.NAME) : null;
            value = uid != null && uid.getValue() != null && uid.getValue().size() == 1 ? uid.getValue().getFirst() : null;
        }
        if (!(value instanceof String || value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long || value instanceof BigInteger)) {
            throw new InvalidAttributeValueException("Relationship " + attribute + " requires nonblank string or integer identifiers, or references with UID");
        }
        var identifier = value.toString();
        if (identifier.isBlank()) {
            throw new InvalidAttributeValueException("Relationship " + attribute + " cannot use blank identifiers");
        }
        if ("integer".equals(type)) {
            try {
                return new BigInteger(identifier).toString();
            } catch (NumberFormatException exception) {
                throw new InvalidAttributeValueException("Relationship " + attribute + " requires integer identifiers", exception);
            }
        }
        return identifier;
    }

    private JsonNode wireValue(String value, String type) {
        return "integer".equals(type) ? FACTORY.numberNode(new BigInteger(value)) : FACTORY.stringNode(value);
    }

    private static void putField(ObjectNode body, AttributePath path, JsonNode value) {
        var fields = path.components();
        var current = body;
        for (int index = 0; index < fields.size() - 1; index++) {
            var name = ((AttributePath.Attribute) fields.get(index)).name();
            var child = current.get(name);
            current = child instanceof ObjectNode object ? object : current.putObject(name);
        }
        current.set(((AttributePath.Attribute) fields.getLast()).name(), value);
    }

    private HttpRequestSpecification request(HttpMethod method, String path, String source) {
        var request = context.rest().newRequest().apiEndpoint(path).httpMethod(method);
        if (path.contains("{source}")) {
            request.pathParameter("source", encodeSegment(source));
        }
        return request;
    }

    private static String encodeSegment(String value) {
        if (".".equals(value) || "..".equals(value)) {
            throw new InvalidAttributeValueException("Relationship path identifier cannot be a dot segment");
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private void mutate(HttpRequestSpecification request, HttpStatusMapper.OperationKind operation) {
        var response = execute(request, HttpResponse.BodyHandlers.ofByteArray());
        if (operation == HttpStatusMapper.OperationKind.DELETE && response.statusCode() == 404) {
            return;
        }
        checkStatus(response, operation);
        if (response.statusCode() == 202 || response.statusCode() == 206 || response.statusCode() == 207) {
            throw new ConnectorException("Relationship " + attribute + " requires synchronous, complete writes; HTTP " + response.statusCode());
        }
    }

    private void checkStatus(HttpResponse<?> response, HttpStatusMapper.OperationKind operation) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw HttpStatusMapper.map(response.statusCode(), operation, response.request().uri().toString(), null, ErrorDetail.extract(response.body()));
        }
    }

    private HttpResponse<?> execute(HttpRequestSpecification request, HttpResponse.BodyHandler<?> handler) {
        try {
            return context.rest().executeRequest(request, handler);
        } catch (InterruptedException exception) {
            throw HttpExceptionMapper.map(exception, endpoint);
        } catch (IOException exception) {
            var mapped = HttpExceptionMapper.unwrapIcf(exception);
            throw mapped != null ? mapped : HttpExceptionMapper.map(exception, endpoint);
        }
    }

    private ConnectorException invalidSnapshot(String detail) {
        return new ConnectorException("Invalid relationship snapshot for " + attribute + " at " + endpoint + ": " + detail);
    }
}
