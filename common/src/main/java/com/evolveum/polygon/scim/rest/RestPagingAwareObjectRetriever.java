package com.evolveum.polygon.scim.rest;

import com.evolveum.polygon.scim.rest.groovy.BatchAwareResultHandler;
import com.evolveum.polygon.scim.rest.schema.MappedObjectClass;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.json.JSONArray;
import org.json.JSONObject;
import com.evolveum.polygon.scim.rest.groovy.RestSearchOperationHandler;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.filter.Filter;

import java.net.http.HttpResponse;

// FIXME: Consider making this JSON agnostic and format / parsing handling will be injected.
public class RestPagingAwareObjectRetriever<T extends RestContext> {

    private final RestSearchOperationHandler specification;
    private final MappedObjectClass objectClass;

    public RestPagingAwareObjectRetriever(MappedObjectClass objectClass, RestSearchOperationHandler<?,?> specification) {
        this.objectClass = objectClass;
        this.specification = specification;
    }

    public void fetch(T context, Filter query, ResultsHandler handler, OperationOptions options) {
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
                    handler.handle(obj);
                    batchProcessed++;
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
                }
                currentPage++;
            } while (shouldContinue);
        } catch (Exception e) {
            // FIXME: Add proper error handling here and maybe translation to ConnID exceptions
            throw new RuntimeException(e);
        }
    }

    private HttpResponse.BodyHandler<Object> bodyHandlerFrom(RestSearchOperationHandler spec) {
        if (JSONObject.class.equals(spec.responseType()) || JSONArray.class.equals(spec.responseType())) {
            return new JacksonBodyHandler(spec.responseType());
        }
        throw new IllegalArgumentException("Unsupported response type: " + spec.responseType());
    }

    private ConnectorObject deserializeFromRemote(Object obj) {
        if (obj instanceof JSONObject remoteObj) {
            var builder = objectClass.newObjectBuilder();
            for (var attributeDef : objectClass.attributes()) {
                if (remoteObj.has(attributeDef.remoteName()) && attributeDef.valueMapping() != null) {
                    var wireValues = remoteObj.get(attributeDef.remoteName());
                    Object connIdValues = null;
                    if (wireValues != null) {
                        connIdValues = attributeDef.valueMapping().toConnIdValues(wireValues);
                    }
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

