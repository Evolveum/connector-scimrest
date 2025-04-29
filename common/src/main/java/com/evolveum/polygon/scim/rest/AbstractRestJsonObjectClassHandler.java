package com.evolveum.polygon.scim.rest;

import com.evolveum.polygon.scim.rest.schema.RestObjectClass;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.json.JSONObject;
import com.evolveum.polygon.scim.rest.groovy.SearchHandler;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.filter.Filter;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public abstract class AbstractRestJsonObjectClassHandler extends AbstractObjectClassHandler {

    RestObjectClass objectClass;
    HttpClient client;

    @Override
    public void executeQuery(Filter query, ResultsHandler handler, OperationOptions options) {
        var spec = createSearchSpecification(query);

        var shouldContinue = true;
        var currentPage = 0;
        var pageLimit = -1;
        var processed = 0;
        try {
            do {
                HttpRequest.Builder requestBuilder = createHttpRequest();
                spec.addUriAndPaging(requestBuilder, currentPage, pageLimit);
                var response = executeRequest(requestBuilder);
                var remoteObjs = spec.extractRemoteObject(response);
                for (var remoteObj : remoteObjs) {
                    ConnectorObject obj = deserializeFromRemote(remoteObj);
                    handler.handle(obj);
                    processed++;
                }
                // FIXME: CHeck totals if available and if processed is less then total should continue = true
                if (processed == 0) {
                    shouldContinue = false;
                }
            } while (shouldContinue);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ConnectorObject deserializeFromRemote(JSONObject remoteObj) {
        var builder = objectClass.newObjectBuilder();
        for (var attributeDef : objectClass.attributes()) {
            var wireValues = remoteObj.get(attributeDef.remoteName());
            Object connIdValues = null;
            if (wireValues != null) {
                connIdValues = attributeDef.valueMapping().toConnIdValue(wireValues);
            }
            if (connIdValues != null) {
                builder.addAttribute(attributeDef.attributeOf(connIdValues));
            }
        }
        return builder.build();
    }

    private HttpResponse<JSONObject> executeRequest(HttpRequest.Builder requestBuilder) throws IOException, InterruptedException {
        requestBuilder.build();
        client.send(requestBuilder.build(), new JsonBodyHandler());
        return null;
    }

    private HttpRequest.Builder createHttpRequest() {
        var builder = HttpRequest.newBuilder();

        return builder;
    }

    protected abstract SearchHandler createSearchSpecification(Filter query);


}

