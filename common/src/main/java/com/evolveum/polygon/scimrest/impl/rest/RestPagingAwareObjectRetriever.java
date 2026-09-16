/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.rest;

import com.evolveum.polygon.conndev.json.JsonAttributeMapping;
import com.evolveum.polygon.scimrest.api.HttpRequestSpecification;
import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.scimrest.JacksonBodyHandler;
import com.evolveum.polygon.conndev.spi.BatchAwareResultHandler;
import com.evolveum.polygon.scimrest.schema.RestObjectClassDefinition;
import groovy.lang.GroovyRuntimeException;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import com.evolveum.polygon.scimrest.groovy.search.RestSearchOperationHandler;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.filter.Filter;

import java.io.IOException;
import java.net.http.HttpResponse;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;

// FIXME: Consider making this JSON agnostic and format / parsing handling will be injected.
public class RestPagingAwareObjectRetriever {

    private final RestSearchOperationHandler specification;
    private final RestObjectClassDefinition objectClass;

    public RestPagingAwareObjectRetriever(RestObjectClassDefinition objectClass, RestSearchOperationHandler<?,?> specification) {
        this.objectClass = objectClass;
        this.specification = specification;
    }

    public void fetch(ContextLookup lookup, Filter query, ResultsHandler handler, OperationOptions options) {
        var context = lookup.get(RestContext.class);
        var shouldContinue = true;
        var currentPage = 1;
        var pageLimit = 25; // FIXME: Make this configurable from builders.
        var totalProcessed = 0;
        do {
            // The per-page fetch (request, status check, object extraction, total count) is the
            // unit that gets the contextual error handling: network/parse/config failures are
            // mapped to the ICF type that matches the situation. handler.handle/batchFinished
            // run outside it, so midPoint-side errors propagate untouched.
            var page = fetchPage(context, currentPage, pageLimit);

            var batchProcessed = 0;
            for (var remoteObj : page.objects()) {
                ConnectorObject obj = deserializeFromRemote(remoteObj, page.endpoint(), currentPage);
                if (obj != null) {
                    shouldContinue = handler.handle(obj);
                    if (!shouldContinue) {
                        break;
                    }
                    batchProcessed++;
                }
            }

            BatchAwareResultHandler.batchFinished(handler);
            totalProcessed += batchProcessed;
            // TODO: Add support for cursor-based continuation https://developer.zendesk.com/api-reference/introduction/pagination/#using-offset-pagination
            // TODO: Maybe paging and cursor API could be merged to being two different implentations of cursor
            if (batchProcessed == 0) {
                shouldContinue = false;
            }
            var totalCount = page.totalCount();
            if (totalCount != null && totalProcessed >= totalCount) {
                shouldContinue = false;
            } else if (batchProcessed < pageLimit) {
                // If we do not have access to total count and page contains less results than page limit
                // we can assume it is last page.
                shouldContinue = false;
            }
            currentPage++;
        } while (shouldContinue);
    }

    private record Page(Iterable<?> objects, Integer totalCount, String endpoint) {
    }

    private Page fetchPage(RestContext context, int page, int pageLimit) {
        HttpRequestSpecification requestBuilder = context.newRequest();
        try {
            specification.addUriAndPaging(requestBuilder, page, pageLimit);
            var bodyHandler = bodyHandlerFrom(specification, "endpoint " + endpointOf(requestBuilder) + ", page " + page);
            var response = context.executeRequest(requestBuilder, bodyHandler);
            checkResponseStatus(response);
            var objects = specification.extractRemoteObject(response);
            var totalCount = specification.extractTotalResultCount(response);
            return new Page(objects, totalCount, endpointOf(requestBuilder));
        } catch (ConnectorException e) {
            // ICF type (parse error, config error, auth error, ...) was already set at the
            // boundary — never re-wrap or relabel it.
            throw e;
        } catch (InterruptedException e) {
            throw HttpExceptionMapper.map(e, endpointOf(requestBuilder));
        } catch (IOException e) {
            // The JDK wraps body-handler errors (e.g. a JSON parse error) into an IOException —
            // surface the original ICF type instead of relabeling it a transient I/O failure.
            var icf = HttpExceptionMapper.unwrapIcf(e);
            if (icf != null) {
                throw icf;
            }
            throw HttpExceptionMapper.map(e, endpointOf(requestBuilder));
        } catch (Exception e) {
            String where = "REST search failed at endpoint " + endpointOf(requestBuilder) + ", page " + page;
            if (e instanceof GroovyRuntimeException) {
                // A typo in the connector's paging/extractor script is a configuration problem.
                throw new ConfigurationException(where + " (search script failed): " + HttpExceptionMapper.causeMessage(e), e);
            }
            throw new ConnectorException(where + ": " + HttpExceptionMapper.causeMessage(e), e);
        }
    }

    /**
     * A non-2xx response from the search endpoint is an error, not an empty result: without this
     * check a 404/500 would silently terminate the scan as a successful zero-result search.
     */
    private static void checkResponseStatus(HttpResponse<?> response) {
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return;
        }
        String uri = String.valueOf(response.request().uri());
        throw HttpStatusMapper.map(status, HttpStatusMapper.OperationKind.SEARCH, uri, null, ErrorDetail.extract(response.body()));
    }

    private static String endpointOf(HttpRequestSpecification spec) {
        String endpoint = spec.getApiEndpoint();
        return endpoint != null && !endpoint.isBlank() ? endpoint : spec.getBaseUri();
    }

    private HttpResponse.BodyHandler<Object> bodyHandlerFrom(RestSearchOperationHandler spec, String context) {
        if (ArrayNode.class.equals(spec.responseType()) || ObjectNode.class.equals(spec.responseType())) {
            return new JacksonBodyHandler<>(spec.responseType(), context);
        }
        throw new ConfigurationException("Unsupported search response type: " + spec.responseType());
    }

    private ConnectorObject deserializeFromRemote(Object obj, String endpoint, int page) {
        if (obj instanceof ObjectNode remoteObj) {
            if (remoteObj.isEmpty()) {
                return null;
            }
            var builder = objectClass.newObjectBuilder();
            for (var attributeDef : objectClass.attributes()) {
                var valueMapping = attributeDef.mapping(JsonAttributeMapping.class);
                if (valueMapping != null) {
                    Object connIdValues = valueMapping.valuesFromObject(remoteObj);
                    if (connIdValues != null) {
                        builder.addAttribute(attributeDef.attributeOf(connIdValues));
                    }
                }
            }
            return builder.build();
        }
        throw new ConnectorException(
                "Search response at endpoint " + endpoint + " page " + page
                        + " contains an unexpected JSON type: " + (obj == null ? "null" : obj.getClass().getName()));
    }

}
