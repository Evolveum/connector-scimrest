/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.rest;

import com.evolveum.polygon.conndev.json.JsonAttributeMapping;
import com.evolveum.polygon.conndev.spi.BatchAwareResultHandler;
import com.evolveum.polygon.scimrest.api.HttpRequestSpecification;
import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.scimrest.JacksonBodyHandler;
import com.evolveum.polygon.scimrest.groovy.connector.RestConnectorContext;
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
import java.util.ArrayList;
import java.util.List;
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
        var converter = lookup.get(RestConnectorContext.class).lookupConverter();
        ResultsHandler effectiveHandler = converter == null
                ? handler
                : obj -> handler.handle(converter.toUserFacing(objectClass.objectClass(), obj));

        var shouldContinue = true;
        var totalProcessed = 0;
        var currentPage = 1;
        var pageLimit = specification.pageLimit();
        var responsePageLimit = specification.responsePageLimit();

        // When the operation options carry a full window (ConnId 1-based record offset + size), fetch
        // exactly that window, assembling it from one or more real pages. Otherwise fall back to
        // scanning the result set page by page until it is exhausted (empty page, total count
        // reached, or a short page).
        Integer pagedOffset = options != null ? options.getPagedResultsOffset() : null;
        Integer pagedSize = options != null ? options.getPageSize() : null;
        var windowRequested = pagedOffset != null && pagedOffset >= 1 && pagedSize != null && pagedSize >= 1;
        long windowStart = windowRequested ? (long) pagedOffset - 1 : 0;
        long windowEnd = windowRequested ? windowStart + pagedSize : 0;
        List<PageRequestInfo> pageRequestInfos = windowRequested
                ? planPages(windowStart, windowEnd, responsePageLimit)
                : List.of();
        var pageRequestInfoIterator = pageRequestInfos.iterator();
        var fullScan = pageRequestInfos.isEmpty();
        // A scan page must not ask for more than the remote returns per real page: a page that is
        // short only because of the server-side cap would be mistaken for the last page.
        var scanPageLimit = responsePageLimit != null
                ? Math.min(pageLimit, responsePageLimit)
                : pageLimit;

        while (shouldContinue) {
            HttpRequestSpecification requestBuilder = context.newRequest();
            PageRequestInfo pageRequestInfo = pageRequestInfoIterator.hasNext()
                    ? pageRequestInfoIterator.next()
                    : null;

            if (pageRequestInfo != null) {
                specification.addUriAndPaging(requestBuilder,
                        pageRequestInfo.getPageRequest().pageOffset(),
                        pageRequestInfo.getPageRequest().pageSize());
                currentPage = pageRequestInfo.getPageRequest().pageOffset();
            } else if (fullScan) {
                specification.addUriAndPaging(requestBuilder, currentPage, scanPageLimit);
            } else {
                // The planned real pages cover the requested logical window.
                break;
            }

            var page = fetchPage(context, requestBuilder, currentPage);
            var remoteObject = page.objects();

            if (pageRequestInfo != null) {
                remoteObject = assembleLogicalPage(windowStart, windowEnd, page.objects(), pageRequestInfo);
            }

            var batchProcessed = 0;
            for (var remoteObj : remoteObject) {
                ConnectorObject obj = deserializeFromRemote(remoteObj, page.endpoint(), currentPage);
                if (obj != null) {
                    shouldContinue = effectiveHandler.handle(obj);
                    if (!shouldContinue) {
                        break;
                    }
                    batchProcessed++;
                }
            }

            BatchAwareResultHandler.batchFinished(effectiveHandler);
            totalProcessed += batchProcessed;

//            // TODO: Add support for cursor-based continuation https://developer.zendesk.com/api-reference/introduction/pagination/#using-offset-pagination
//            // TODO: Maybe paging and cursor API could be merged to being two different implentations of cursor
            if (batchProcessed == 0) {
                break;
            }
            var totalCount = page.totalCount();
            if (totalCount != null && totalProcessed >= totalCount) {
                break;
            } else if (pageRequestInfo != null) {
                if (pageRequestInfo.isResultSetExhausted()) {
                    break;
                }
                // Honor a ResultsHandler that asked to stop: do not fetch the remaining real
                // pages of the logical window.
                shouldContinue = shouldContinue && pageRequestInfoIterator.hasNext();
            } else {
                // Full scan: a short page means the result set is exhausted.
                if (batchProcessed < scanPageLimit) {
                    break;
                }
                currentPage++;
            }
        }
    }

    private record Page(Iterable<?> objects, Integer totalCount, String endpoint) {
    }

    private Page fetchPage(RestContext context, HttpRequestSpecification requestBuilder, int page) {
        try {
            var bodyHandler = bodyHandlerFrom(specification,
                    "endpoint " + endpointOf(requestBuilder) + ", page " + page);
            var response = context.executeRequest(requestBuilder, bodyHandler);
            if (response.statusCode() == 404 && specification.notFoundIsNoResult()) {
                // The endpoint declares a 404 as "no result" (e.g. a by-id lookup of a
                // nonexistent object): the search is simply empty, not an error.
                return new Page(List.of(), 0, endpointOf(requestBuilder));
            }
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
     *
     * <p>An exception: a 404 from an endpoint that declares {@code notFoundIsNoResult} is
     * handled by {@link #fetchPage} as an empty result set and never reaches this check.</p>
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

    public static List<PageRequestInfo> planPages(long windowStart, long windowEnd, Integer maxPageSize) {
        long windowSize = windowEnd - windowStart;
        int realPageSize = maxPageSize == null || maxPageSize < 1
                ? (int) windowSize
                : (int) Math.min(windowSize, maxPageSize);

        int firstRealPage = (int) (windowStart / realPageSize) + 1;
        int lastRealPage = (int) ((windowEnd - 1) / realPageSize) + 1;

        List<PageRequestInfo> plan = new ArrayList<>();
        for (int rp = firstRealPage; rp <= lastRealPage; rp++) {
            plan.add(new PageRequestInfo(rp, realPageSize));
        }
        return plan;
    }

    public static List<?> assembleLogicalPage(
            long windowStart, long windowEnd,
            Iterable<?> realPageItems, PageRequestInfo pageRequestInfo) {

        List<Object> concatenated = new ArrayList<>();
        for (var item : realPageItems) {
            concatenated.add(item);
        }
        var pageRequest = pageRequestInfo.getPageRequest();
        if (concatenated.size() < pageRequest.pageSize()) {
            pageRequestInfo.setRecordSetExhausted(true);
        }

        long currentRealPageStartIndex = (long) (pageRequest.pageOffset() - 1) * pageRequest.pageSize();
        int fromInclusive = (int) Math.max(0, windowStart - currentRealPageStartIndex);
        int toExclusive = (int) Math.min(windowEnd - currentRealPageStartIndex, concatenated.size());

        if (fromInclusive >= toExclusive) {
            return List.of();
        }
        return concatenated.subList(fromInclusive, toExclusive);
    }

    private static class PageRequestInfo {
        private final PageRequest pageRequests;
        private boolean recordSetExhausted = false;

        public PageRequestInfo(Integer pageOffset, Integer pageSize) {
            this.pageRequests = new PageRequest(pageOffset, pageSize);
        }

        public PageRequest getPageRequest() {
            return pageRequests;
        }

        public boolean isResultSetExhausted() {
            return recordSetExhausted;
        }

        public void setRecordSetExhausted(boolean recordSetExhausted) {
            this.recordSetExhausted = recordSetExhausted;
        }

        private record PageRequest(int pageOffset, int pageSize) {
        }
    }


}
