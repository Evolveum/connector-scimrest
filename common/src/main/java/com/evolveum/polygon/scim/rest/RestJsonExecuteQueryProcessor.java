package com.evolveum.polygon.scim.rest;

import com.evolveum.polygon.scim.rest.schema.RestObjectClass;
import com.evolveum.polygon.scim.rest.spi.ExecuteQueryProcessor;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.json.JSONArray;
import org.json.JSONObject;
import com.evolveum.polygon.scim.rest.groovy.RestSearchOperationHandler;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.filter.Filter;

import java.net.http.HttpResponse;

// FIXME: Consider making this JSON agnostic and format / parsing handling will be injected.
public abstract class RestJsonExecuteQueryProcessor<T extends RestContext> implements ExecuteQueryProcessor<T> {

    private RestObjectClass objectClass;

    public RestJsonExecuteQueryProcessor(RestObjectClass objectClass) {
        this.objectClass = objectClass;
    }

    @Override
    public void executeQuery(T context, Filter query, ResultsHandler handler, OperationOptions options) {
        var spec = createSearchSpecification(query);

        var shouldContinue = true;
        var currentPage = 1;
        var pageLimit = 25; // FIXME: Make this configurable from builders.
        var totalProcessed = 0;
        try {
            do {
                var batchProcessed = 0;
                RestContext.RequestBuilder requestBuilder = context.newAuthorizedRequest();
                spec.addUriAndPaging(requestBuilder, currentPage, pageLimit);
                var bodyHandler = bodyHandlerFrom(spec);
                var response = context.executeRequest(requestBuilder, bodyHandler);
                var remoteObjs = spec.extractRemoteObject(response);

                for (var remoteObj : remoteObjs) {
                    ConnectorObject obj = deserializeFromRemote(remoteObj);
                    handler.handle(obj);
                    batchProcessed++;
                }
                totalProcessed += batchProcessed;
                // FIXME: Check totals if available and if processed is less then total should continue = true
                if (batchProcessed == 0) {
                    shouldContinue = false;
                }
                var totalCount = spec.extractTotalResultCount(response);
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
                if (remoteObj.has(attributeDef.remoteName())) {
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

    protected abstract RestSearchOperationHandler createSearchSpecification(Filter query);


}

