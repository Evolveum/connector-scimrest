/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.rest;

import com.evolveum.polygon.scimrest.ContextLookup;
import com.evolveum.polygon.scimrest.JacksonBodyHandler;
import com.evolveum.polygon.scimrest.spi.BatchAwareResultHandler;
import com.evolveum.polygon.scimrest.schema.JsonAttributeMapping;
import com.evolveum.polygon.scimrest.schema.MappedObjectClass;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import com.evolveum.polygon.scimrest.groovy.RestSearchOperationHandler;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.filter.Filter;

import java.net.http.HttpResponse;

// FIXME: Consider making this JSON agnostic and format / parsing handling will be injected.
public class RestPagingAwareObjectRetriever {

    private final RestSearchOperationHandler specification;
    private final MappedObjectClass objectClass;

    public RestPagingAwareObjectRetriever(MappedObjectClass objectClass, RestSearchOperationHandler<?,?> specification) {
        this.objectClass = objectClass;
        this.specification = specification;
    }

    public void fetch(ContextLookup lookup, Filter query, ResultsHandler handler, OperationOptions options) {
        var context = lookup.get(RestContext.class);
        var shouldContinue = true;
        var currentPage = 1;
        var pageLimit = 25; // FIXME: Make this configurable from builders.
        var totalProcessed = 0;
        try {
            do {
                var batchProcessed = 0;
                RestContext.RequestBuilder requestBuilder = context.newAuthorizedRequest();
                specification.addUriAndPaging(requestBuilder, currentPage, pageLimit);
                var bodyHandler = bodyHandlerFrom(specification);
                var response = context.executeRequest(requestBuilder, bodyHandler);

                var remoteObjs = specification.extractRemoteObject(response);

                for (var remoteObj : remoteObjs) {
                    ConnectorObject obj = deserializeFromRemote(remoteObj);
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
                var totalCount = specification.extractTotalResultCount(response);
                if (totalCount != null && totalProcessed >= totalCount) {
                    shouldContinue = false;
                } else if (batchProcessed < pageLimit) {
                    // If we do not have access to total count and page contains less results than page limit
                    // we can assume it is last page.
                    shouldContinue = false;
                }
                currentPage++;
            } while (shouldContinue);
        } catch (Exception e) {
            // FIXME: Add proper error handling here and maybe translation to ConnID exceptions
            throw new RuntimeException(e);
        }
    }

    private HttpResponse.BodyHandler<Object> bodyHandlerFrom(RestSearchOperationHandler spec) {
        if (ArrayNode.class.equals(spec.responseType()) || ObjectNode.class.equals(spec.responseType())) {
            return new JacksonBodyHandler(spec.responseType());
        }
        throw new IllegalArgumentException("Unsupported response type: " + spec.responseType());
    }

    private ConnectorObject deserializeFromRemote(Object obj) {
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
        throw new IllegalArgumentException("Unexpected object type: " + obj.getClass());
    }

}

