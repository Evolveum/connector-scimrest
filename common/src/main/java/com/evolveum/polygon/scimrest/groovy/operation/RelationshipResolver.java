/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 */
package com.evolveum.polygon.scimrest.groovy.operation;

import com.evolveum.polygon.conndev.api.AttributePath;
import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.build.api.AttributeResolverBuilder.ResolutionType;
import com.evolveum.polygon.conndev.json.JsonAttributeMapping;
import com.evolveum.polygon.conndev.schema.BaseAttributeDefinition;
import com.evolveum.polygon.conndev.spi.AttributeResolver;
import com.evolveum.polygon.scimrest.JacksonBodyHandler;
import com.evolveum.polygon.scimrest.api.HttpRequestSpecification;
import com.evolveum.polygon.scimrest.groovy.connector.RestConnectorContext;
import com.evolveum.polygon.scimrest.groovy.api.PagingInfo;
import com.evolveum.polygon.scimrest.groovy.search.DeclarativePagingHandler;
import com.evolveum.polygon.scimrest.impl.rest.ErrorDetail;
import com.evolveum.polygon.scimrest.impl.rest.HttpExceptionMapper;
import com.evolveum.polygon.scimrest.impl.rest.HttpStatusMapper;
import com.evolveum.polygon.scimrest.impl.rest.RestContext;
import com.evolveum.polygon.scimrest.schema.RestAttributeDefinition;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObjectReference;
import org.identityconnectors.framework.common.objects.ObjectClass;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

/**
Resolves all membership pages independently of the outer object-search window. Values are added only after an empty page confirms completion.
*/
final class RelationshipResolver implements AttributeResolver {

    private final RestAttributeDefinition attribute;
    private final ObjectClass targetClass;
    private final String endpoint;
    private final String sourceParameter;
    private final String sourceTemplate;
    private final AttributePath objectsPath;
    private final AttributePath targetField;
    private final AttributePath sourceField;
    private final int pageSize;
    private final DeclarativePagingHandler pagingHandler;
    private final int initialOffset;
    private final int maxPages;

    RelationshipResolver(RestAttributeDefinition attribute, ObjectClass targetClass, String endpoint, String sourceParameter, String sourceTemplate,
            AttributePath objectsPath, AttributePath targetField, AttributePath sourceField, int pageSize, DeclarativePagingHandler pagingHandler,
            int initialOffset, int maxPages) {
        this.attribute = attribute;
        this.targetClass = targetClass;
        this.endpoint = endpoint;
        this.sourceParameter = sourceParameter;
        this.sourceTemplate = sourceTemplate;
        this.objectsPath = objectsPath;
        this.targetField = targetField;
        this.sourceField = sourceField;
        this.pageSize = pageSize;
        this.pagingHandler = pagingHandler;
        this.initialOffset = initialOffset;
        this.maxPages = maxPages;
    }

    @Override
    public Set<BaseAttributeDefinition> getSupportedAttributes() {
        return Set.of(attribute);
    }

    @Override
    public ResolutionType resolutionType() {
        return ResolutionType.PER_OBJECT;
    }

    @Override
    public void resolveSingle(ContextLookup lookup, ConnectorObjectBuilder builder) {
        var source = builder.build().getUid().getUidValue();
        if (source == null || source.isBlank()) {
            throw invalid("the current object has no UID");
        }
        var context = lookup.get(RestConnectorContext.class).rest();
        var targets = new LinkedHashSet<String>();
        long offset = initialOffset;
        for (int page = 0; page < maxPages; page++) {
            var records = fetchPage(context, source, offset, page + 1);
            if (records.isEmpty()) {
                var references = new ArrayList<ConnectorObjectReference>();
                for (var target : targets) {
                    var reference = new ConnectorObjectBuilder().setObjectClass(targetClass).setUid(target).buildIdentification();
                    references.add(new ConnectorObjectReference(reference));
                }
                builder.addAttribute(attribute.attributeOf(references));
                return;
            }
            for (var record : records) {
                if (!record.isObject()) {
                    throw invalid("each membership record must be a JSON object");
                }
                if (sourceField != null && !source.equals(identifier(record, sourceField, "sourceField"))) {
                    continue;
                }
                targets.add(identifier(record, targetField, "targetField"));
            }
            offset += records.size();
        }
        throw invalid("maxPages was reached before an empty page confirmed completion");
    }

    private JsonNode fetchPage(RestContext context, String source, long offset, int page) {
        var request = context.newRequest().apiEndpoint(endpoint);
        pagingHandler.handlePaging(request, new PagingInfo(pageSize, page), offset);
        if (endpoint.contains("{source}")) {
            if (source.equals(".") || source.equals("..")) {
                throw invalid("the source UID cannot be a URI dot segment");
            }
            request.pathParameter("source", URLEncoder.encode(source, StandardCharsets.UTF_8).replace("+", "%20"));
        }
        if (sourceParameter != null) {
            request.queryParameter(sourceParameter, sourceTemplate.replace("{uid}", source));
        }
        var response = execute(context, request, page);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw HttpStatusMapper.map(response.statusCode(), HttpStatusMapper.OperationKind.SEARCH, response.request().uri().toString(),
                    null, ErrorDetail.extract(response.body()));
        }
        if (response.statusCode() != 200 || !(response.body() instanceof JsonNode body)) {
            throw invalid("expected HTTP 200 with a JSON collection on page " + page);
        }
        var records = objectsPath == null ? body : objectsPath.resolve(body, JsonAttributeMapping.NULLABLE_PATH_RESOLVER);
        if (records == null || !records.isArray()) {
            throw invalid("objectsPath must select an array on page " + page);
        }
        return records;
    }

    private HttpResponse<?> execute(RestContext context, HttpRequestSpecification request, int page) {
        try {
            return context.executeRequest(request, new JacksonBodyHandler<>(JsonNode.class, "relationship " + attribute.connId().getName()
                    + " at endpoint " + endpoint + ", page " + page));
        } catch (InterruptedException exception) {
            throw HttpExceptionMapper.map(exception, endpoint);
        } catch (IOException exception) {
            var mapped = HttpExceptionMapper.unwrapIcf(exception);
            throw mapped != null ? mapped : HttpExceptionMapper.map(exception, endpoint);
        }
    }

    private String identifier(JsonNode record, AttributePath path, String name) {
        var value = path.resolve(record, JsonAttributeMapping.NULLABLE_PATH_RESOLVER);
        if (value != null && value.isString() && !value.asString().isBlank()) {
            return value.asString();
        }
        if (value != null && value.isIntegralNumber()) {
            return value.bigIntegerValue().toString();
        }
        throw invalid(name + " must select a nonblank string or integer identifier");
    }

    private ConnectorException invalid(String message) {
        return new ConnectorException("Invalid relationship records for attribute '" + attribute.connId().getName() + "' at " + endpoint + ": " + message);
    }
}
