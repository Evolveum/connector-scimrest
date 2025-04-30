package com.evolveum.polygon.scim.rest;

import com.evolveum.polygon.scim.rest.schema.RestObjectClass;
import com.evolveum.polygon.scim.rest.spi.ExecuteQueryProcessor;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.json.JSONObject;
import com.evolveum.polygon.scim.rest.groovy.SearchHandler;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.filter.Filter;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

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
        var pageLimit = 25;
        var totalProcessed = 0;
        try {
            do {
                var batchProcessed = 0;
                RestContext.RequestBuilder requestBuilder = context.newAuthorizedRequest();
                spec.addUriAndPaging(requestBuilder, currentPage, pageLimit);
                var response = context.executeRequest(requestBuilder, new JsonBodyHandler());
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
            throw new RuntimeException(e);
        }
    }

    private ConnectorObject deserializeFromRemote(JSONObject remoteObj) {
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

    protected abstract SearchHandler createSearchSpecification(Filter query);


}

