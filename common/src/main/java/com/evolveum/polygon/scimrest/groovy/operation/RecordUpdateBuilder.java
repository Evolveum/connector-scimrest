/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 */
package com.evolveum.polygon.scimrest.groovy.operation;

import com.evolveum.polygon.conndev.api.AttributePath;
import com.evolveum.polygon.conndev.api.AttributePathDeclaration;
import com.evolveum.polygon.conndev.api.BasicJsonPathFormat;
import com.evolveum.polygon.conndev.api.JavaPathFormat;
import com.evolveum.polygon.conndev.api.ParsingException;
import com.evolveum.polygon.conndev.spi.UpdateOperationHandler;
import com.evolveum.polygon.scimrest.groovy.api.RelationshipRecordsBuilder;
import com.evolveum.polygon.scimrest.groovy.connector.RestConnectorContext;
import com.evolveum.polygon.scimrest.groovy.search.DeclarativePagingHandler;
import com.evolveum.polygon.scimrest.groovy.search.EndpointBasedSearchBuilder.PagingParameter;
import com.evolveum.polygon.scimrest.schema.RestAttributeDefinition;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.objects.ConnectorObjectReference;

import java.math.BigInteger;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class RecordUpdateBuilder implements RelationshipRecordsBuilder {

    private final RestConnectorContext context;
    private final RestAttributeDefinition attribute;
    private String endpoint;
    private String deleteEndpoint;
    private AttributePathDeclaration<?, ?> sourceField;
    private AttributePathDeclaration<?, ?> targetField;
    private AttributePathDeclaration<?, ?> idField;
    private AttributePathDeclaration<?, ?> objectsPath;
    private String sourceParameter;
    private String sourceType = "string";
    private String targetType = "string";
    private int pageSize;
    private final List<PagingParameter> parameters = new ArrayList<>();
    private int initialOffset;
    private int maxPages = 1000;

    RecordUpdateBuilder(RestConnectorContext context, RestAttributeDefinition attribute) {
        this.context = context;
        this.attribute = attribute;
    }

    @Override
    public RelationshipRecordsBuilder endpoint(String path) {
        endpoint = path;
        return this;
    }

    @Override
    public RelationshipRecordsBuilder deleteEndpoint(String path) {
        deleteEndpoint = path;
        return this;
    }

    @Override
    public RelationshipRecordsBuilder sourceField(AttributePathDeclaration<?, ?> path) {
        sourceField = normalizePath(path);
        return this;
    }

    @Override
    public RelationshipRecordsBuilder targetField(AttributePathDeclaration<?, ?> path) {
        targetField = normalizePath(path);
        return this;
    }

    @Override
    public RelationshipRecordsBuilder idField(AttributePathDeclaration<?, ?> path) {
        idField = normalizePath(path);
        return this;
    }

    @Override
    public RelationshipRecordsBuilder objectsPath(AttributePathDeclaration<?, ?> path) {
        objectsPath = normalizePath(path);
        return this;
    }

    @Override
    public RelationshipRecordsBuilder sourceParameter(String name) {
        sourceParameter = name;
        return this;
    }

    @Override
    public RelationshipRecordsBuilder sourceType(String type) {
        sourceType = type;
        return this;
    }

    @Override
    public RelationshipRecordsBuilder targetType(String type) {
        targetType = type;
        return this;
    }

    @Override
    public RelationshipRecordsBuilder pageSize(int size) {
        pageSize = size;
        return this;
    }

    @Override
    public RelationshipRecordsBuilder pagingParameter(String token, String location, String name) {
        parameters.add(new PagingParameter(token, location, name));
        return this;
    }

    @Override
    public RelationshipRecordsBuilder initialOffset(int offset) {
        initialOffset = offset;
        return this;
    }

    @Override
    public RelationshipRecordsBuilder maxPages(int count) {
        maxPages = count;
        return this;
    }

    String attributeName() {
        return attribute.connId().getName();
    }

    UpdateOperationHandler build() {
        validateAttribute();
        validateEndpoint(endpoint, false);
        var deletionPath = deleteEndpoint != null ? deleteEndpoint : endpoint.replaceAll("/+$", "") + "/{id}";
        validateEndpoint(deletionPath, true);
        var sourcePath = requireField(sourceField, "sourceField", true);
        var targetPath = requireField(targetField, "targetField", true);
        var identifierPath = requireField(idField, "idField", false);
        rejectOverlap(sourcePath, targetPath, "sourceField", "targetField");
        rejectOverlap(sourcePath, identifierPath, "sourceField", "idField");
        rejectOverlap(targetPath, identifierPath, "targetField", "idField");
        validateType(sourceType, "sourceType");
        validateType(targetType, "targetType");
        validatePaging();
        return new RelationshipHandler(context, attributeName(), endpoint, deletionPath, sourcePath, targetPath, identifierPath,
                objectsPath != null ? resolvePath(objectsPath, "objectsPath") : null, sourceParameter, sourceType, targetType,
                pageSize, new DeclarativePagingHandler(List.copyOf(parameters)), initialOffset, maxPages);
    }

    private void validateAttribute() {
        var definition = attribute.connId();
        if (!definition.isMultiValued() || !definition.isUpdateable()) {
            throw invalid("requires a multivalued, updateable attribute");
        }
        var types = Set.of(String.class, Byte.class, Short.class, Integer.class, Long.class, BigInteger.class, ConnectorObjectReference.class);
        if (!types.contains(definition.getType())) {
            throw invalid("requires string, integral, or reference attribute values");
        }
        if (attribute.lookup() != null) {
            throw invalid("does not support lookup mappings; configure native target identifiers");
        }
    }

    private AttributePath requireField(AttributePathDeclaration<?, ?> declaration, String name, boolean writable) {
        if (declaration == null) {
            throw invalid("requires " + name);
        }
        var path = resolvePath(declaration, name);
        if (path.components().isEmpty()) {
            throw invalid(name + " must select a record field");
        }
        if (writable && path.components().stream().anyMatch(component -> !(component instanceof AttributePath.Attribute))) {
            throw invalid(name + " must contain only object field components");
        }
        return path;
    }

    private AttributePath resolvePath(AttributePathDeclaration<?, ?> declaration, String name) {
        try {
            return declaration.actual();
        } catch (ParsingException exception) {
            throw new ConfigurationException("Invalid " + name + " for relationship attribute '" + attributeName() + "': "
                    + exception.getMessage() + " (" + declaration.value().location() + ")", exception);
        }
    }

    private AttributePathDeclaration<?, ?> normalizePath(AttributePathDeclaration<?, ?> declaration) {
        if (declaration != null && declaration.type().value() instanceof BasicJsonPathFormat
                && declaration.value().value() instanceof String value && value.matches("[A-Za-z_][A-Za-z0-9_-]*")) {
            return AttributePathDeclaration.of(declaration.type().derived(JavaPathFormat.INSTANCE), declaration.value().derived(AttributePath.of(value)));
        }
        return declaration;
    }

    private void rejectOverlap(AttributePath first, AttributePath second, String firstName, String secondName) {
        int sharedSize = Math.min(first.components().size(), second.components().size());
        if (first.components().subList(0, sharedSize).equals(second.components().subList(0, sharedSize))) {
            throw invalid(firstName + " and " + secondName + " must not overlap");
        }
    }

    private void validateEndpoint(String path, boolean deletion) {
        if (path == null || path.isBlank()) {
            throw invalid("requires " + (deletion ? "deleteEndpoint" : "endpoint"));
        }
        String decoded;
        try {
            decoded = URLDecoder.decode(path.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw invalid("contains an invalid endpoint escape");
        }
        if (decoded.startsWith("//") || decoded.contains(":") || decoded.contains("?") || decoded.contains("#") || decoded.contains("\\")) {
            throw invalid("endpoint must be a relative API path without a query or fragment");
        }
        if (decoded.replace("/", "").isBlank()) {
            throw invalid("endpoint must identify a collection");
        }
        for (String segment : decoded.split("/")) {
            if (segment.equals(".") || segment.equals("..")) {
                throw invalid("endpoint must not contain traversal segments");
            }
        }
        String literal = path.replace("{source}", "");
        if (deletion) {
            if (!path.contains("{id}")) {
                throw invalid("deleteEndpoint must contain {id}");
            }
            literal = literal.replace("{id}", "");
        }
        if (literal.contains("{") || literal.contains("}")) {
            throw invalid("endpoint contains an unsupported placeholder");
        }
    }

    private void validateType(String type, String name) {
        if (!"string".equals(type) && !"integer".equals(type)) {
            throw invalid(name + " must be string or integer");
        }
    }

    private void validatePaging() {
        if (pageSize < 0 || initialOffset < 0 || maxPages <= 0) {
            throw invalid("pageSize and initialOffset must be nonnegative; maxPages must be positive");
        }
        if (pageSize == 0 && !parameters.isEmpty()) {
            throw invalid("paging parameters require a positive pageSize");
        }
        DeclarativePagingHandler.validateOffset(parameters, endpoint, sourceParameter, pageSize > 0);
    }

    private ConfigurationException invalid(String message) {
        return new ConfigurationException("Relationship records for attribute '" + attributeName() + "' " + message);
    }
}
