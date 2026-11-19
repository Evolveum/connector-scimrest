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
import com.evolveum.polygon.conndev.spi.AttributeResolver;
import com.evolveum.polygon.scimrest.config.RelationshipContext;
import com.evolveum.polygon.scimrest.groovy.api.EndpointBuilder.FixedRequest;
import com.evolveum.polygon.scimrest.groovy.api.RelationshipReadBuilder;
import com.evolveum.polygon.scimrest.groovy.endpoint.QueryRequestBuilderImpl;
import com.evolveum.polygon.scimrest.groovy.search.DeclarativePagingHandler;
import com.evolveum.polygon.scimrest.groovy.search.EndpointBasedSearchBuilder.PagingParameter;
import com.evolveum.polygon.scimrest.schema.RestAttributeDefinition;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.objects.ConnectorObjectReference;
import org.identityconnectors.framework.common.objects.ObjectClass;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

final class RecordSearchBuilder implements RelationshipReadBuilder {

    private final RestAttributeDefinition attribute;
    private final RecordRequest request = new RecordRequest();
    private String endpoint;
    private String sourceParameter;
    private String sourceTemplate = "{uid}";
    private AttributePathDeclaration<?, ?> objectsPath;
    private AttributePathDeclaration<?, ?> targetField;
    private AttributePathDeclaration<?, ?> sourceField;
    private int pageSize = 100;
    private final List<PagingParameter> parameters = new ArrayList<>();
    private int initialOffset;
    private int maxPages = 1000;

    RecordSearchBuilder(RestAttributeDefinition attribute) {
        this.attribute = attribute;
    }

    @Override
    public FixedRequest request() {
        return request;
    }

    private static final class RecordRequest implements FixedRequest {
        private final QueryRequestBuilderImpl settings = new QueryRequestBuilderImpl();

        @Override
        public FixedRequest queryParameter(String name, Object value) {
            settings.queryParameter(name, value);
            return this;
        }

        @Override
        public FixedRequest header(String name, String value) {
            settings.header(name, value);
            return this;
        }
    }

    @Override
    public RelationshipReadBuilder endpoint(String path) {
        endpoint = path;
        return this;
    }

    @Override
    public RelationshipReadBuilder sourceParameter(String name) {
        sourceParameter = name;
        return this;
    }

    @Override
    public RelationshipReadBuilder sourceTemplate(String template) {
        sourceTemplate = template;
        return this;
    }

    @Override
    public RelationshipReadBuilder objectsPath(AttributePathDeclaration<?, ?> path) {
        objectsPath = normalizePath(path);
        return this;
    }

    @Override
    public RelationshipReadBuilder targetField(AttributePathDeclaration<?, ?> path) {
        targetField = normalizePath(path);
        return this;
    }

    @Override
    public RelationshipReadBuilder sourceField(AttributePathDeclaration<?, ?> path) {
        sourceField = normalizePath(path);
        return this;
    }

    @Override
    public RelationshipReadBuilder pageSize(int size) {
        pageSize = size;
        return this;
    }

    @Override
    public RelationshipReadBuilder pagingParameter(String token, String location, String name) {
        parameters.add(new PagingParameter(token, location, name));
        return this;
    }

    @Override
    public RelationshipReadBuilder initialOffset(int offset) {
        initialOffset = offset;
        return this;
    }

    @Override
    public RelationshipReadBuilder maxPages(int count) {
        maxPages = count;
        return this;
    }

    RelationshipContext configuration() {
        return new RelationshipContext(attributeName(), endpoint);
    }

    String attributeName() {
        return attribute.connId().getName();
    }

    AttributeResolver build() {
        var definition = attribute.connId();
        if (!definition.isMultiValued() || !ConnectorObjectReference.class.equals(definition.getType())) {
            throw invalid("requires a multivalued reference attribute");
        }
        var targetClass = definition.getReferencedObjectClassName();
        if (targetClass == null || targetClass.isBlank()) {
            throw invalid("requires the reference's objectClass in the schema");
        }
        if (attribute.resolver() != null || attribute.lookup() != null) {
            throw invalid("cannot be combined with another resolver or a lookup mapping");
        }
        validateEndpoint();
        validateParameters();
        if (targetField == null) {
            throw invalid("requires targetField");
        }
        var targetPath = resolvePath(targetField, "targetField");
        var sourcePath = sourceField != null ? resolvePath(sourceField, "sourceField") : null;
        if (targetPath.components().isEmpty() || sourcePath != null && sourcePath.components().isEmpty()) {
            throw invalid("sourceField and targetField must select a record field");
        }
        return new RelationshipResolver(attribute, new ObjectClass(targetClass), endpoint, sourceParameter, sourceTemplate,
                objectsPath != null ? resolvePath(objectsPath, "objectsPath") : null, targetPath, sourcePath,
                pageSize, new DeclarativePagingHandler(List.copyOf(parameters)), initialOffset, maxPages,
                Map.copyOf(request.settings.queryParameters), Map.copyOf(request.settings.headers));
    }

    private void validateEndpoint() {
        if (endpoint == null || endpoint.isBlank()) {
            throw invalid("requires endpoint");
        }
        String decoded;
        try {
            decoded = URLDecoder.decode(endpoint.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw invalid("endpoint contains an invalid percent escape");
        }
        if (decoded.startsWith("//") || decoded.matches("^[A-Za-z][A-Za-z0-9+.-]*:.*") || decoded.contains("?") || decoded.contains("#") || decoded.contains("\\")) {
            throw invalid("endpoint must be a relative API path without a query or fragment");
        }
        if (decoded.replace("/", "").isBlank()) {
            throw invalid("endpoint must identify a collection");
        }
        for (var segment : decoded.split("/")) {
            if (segment.equals(".") || segment.equals("..")) {
                throw invalid("endpoint must not contain traversal segments");
            }
        }
        var literal = endpoint.replace("{source}", "");
        if (literal.contains("{") || literal.contains("}")) {
            throw invalid("endpoint contains an unsupported placeholder");
        }
        if (sourceParameter == null && !endpoint.contains("{source}")) {
            throw invalid("requires sourceParameter or an endpoint containing {source}");
        }
    }

    private void validateParameters() {
        if (pageSize <= 0 || initialOffset < 0 || maxPages <= 0) {
            throw invalid("pageSize and maxPages must be positive; initialOffset must be nonnegative");
        }
        DeclarativePagingHandler.validateOffset(parameters, endpoint, sourceParameter, true, attributeName());
        validateRequest();
        if (sourceTemplate == null || !sourceTemplate.contains("{uid}")) {
            throw invalid("sourceTemplate must contain {uid}");
        }
        var literal = sourceTemplate.replace("{uid}", "");
        if (Pattern.compile("\\{[A-Za-z_][A-Za-z0-9_]*}").matcher(literal).find()) {
            throw invalid("sourceTemplate contains an unsupported placeholder");
        }
        if (sourceParameter == null && !"{uid}".equals(sourceTemplate)) {
            throw invalid("a custom sourceTemplate requires sourceParameter");
        }
    }

    private void validateRequest() {
        var settings = request.settings;
        var queryNames = new HashSet<String>();
        for (var name : settings.queryParameters.keySet()) {
            var decoded = queryName(name);
            if (decoded.equals(sourceParameter)) {
                throw invalid("fixed query parameter '" + name + "' conflicts with sourceParameter");
            }
            queryNames.add(decoded);
        }
        for (var name : settings.headers.keySet()) {
            if (name == null || !name.matches("[!#$%&'*+.^_`|~A-Za-z0-9-]+")) {
                throw invalid("invalid fixed header name '" + name + "'");
            }
        }
        for (var parameter : parameters) {
            boolean conflict = "query".equals(parameter.location()) ? queryNames.contains(parameter.name())
                    : settings.headers.keySet().stream().anyMatch(parameter.name()::equalsIgnoreCase);
            if (conflict) {
                throw invalid("fixed " + parameter.location() + " parameter '" + parameter.name() + "' conflicts with paging token '" + parameter.token() + "'");
            }
        }
    }

    private String queryName(String name) {
        // The HTTP converter writes names verbatim. Compare the key the server receives.
        if (name == null || name.isBlank() || name.matches(".*[&;=#].*")) {
            throw invalid("invalid fixed query parameter name '" + name + "'");
        }
        try {
            return URLDecoder.decode(URI.create("?" + name).getRawQuery(), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw invalid("invalid fixed query parameter name '" + name + "'");
        }
    }

    private AttributePath resolvePath(AttributePathDeclaration<?, ?> declaration, String name) {
        try {
            return declaration.actual();
        } catch (ParsingException exception) {
            throw configuration().invalid("Invalid " + name + ": " + exception.getMessage(), exception);
        }
    }

    private AttributePathDeclaration<?, ?> normalizePath(AttributePathDeclaration<?, ?> declaration) {
        if (declaration != null && declaration.type().value() instanceof BasicJsonPathFormat
                && declaration.value().value() instanceof String value && value.matches("[A-Za-z_][A-Za-z0-9_-]*")) {
            return AttributePathDeclaration.of(declaration.type().derived(JavaPathFormat.INSTANCE), declaration.value().derived(AttributePath.of(value)));
        }
        return declaration;
    }

    private ConfigurationException invalid(String message) {
        return configuration().invalid(message);
    }
}
