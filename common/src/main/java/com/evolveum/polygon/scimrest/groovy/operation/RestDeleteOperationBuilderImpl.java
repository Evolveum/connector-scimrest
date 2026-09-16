/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.scimrest.groovy.operation;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.scimrest.groovy.endpoint.DeclarativeResponseBuilder;
import com.evolveum.polygon.scimrest.groovy.endpoint.DeclarativeRequestBuilder;
import com.evolveum.polygon.scimrest.groovy.endpoint.AbstractSingleObjectEndpointBuilder;
import com.evolveum.polygon.scimrest.groovy.schema.BaseOperationSupportBuilder;
import com.evolveum.polygon.scimrest.groovy.connector.RestConnectorContext;

import com.evolveum.polygon.conndev.groovy.AbstractDeleteOperationBuilder;
import com.evolveum.polygon.scimrest.JacksonBodyHandler;
import com.evolveum.polygon.scimrest.groovy.api.EndpointBuilder;
import com.evolveum.polygon.scimrest.groovy.api.HttpMethod;
import com.evolveum.polygon.scimrest.groovy.api.RestDeleteOperationBuilder;
import com.evolveum.polygon.conndev.spi.DeleteOperationHandler;
import com.evolveum.polygon.scimrest.impl.scim.ScimDeleteHandler;
import com.evolveum.polygon.scimrest.impl.rest.ErrorDetail;
import com.evolveum.polygon.scimrest.impl.rest.HttpExceptionMapper;
import com.evolveum.polygon.scimrest.impl.rest.HttpStatusMapper;
import com.evolveum.polygon.scimrest.schema.RestObjectClassDefinition;
import tools.jackson.databind.node.ObjectNode;
import groovy.lang.Closure;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class RestDeleteOperationBuilderImpl extends AbstractDeleteOperationBuilder<RestObjectClassDefinition>
        implements RestDeleteOperationBuilder {

    private final List<EndpointImpl> endpoints = new ArrayList<>();
    private ScimImpl scim;

    public RestDeleteOperationBuilderImpl(BaseOperationSupportBuilder parent) {
        super(parent);
    }

    @Override
    public Endpoint endpoint(HttpMethod method, String path) {
        for (EndpointImpl endpoint : endpoints) {
            if (endpoint.matches(method, path)) {
                return endpoint;
            }
        }
        var endpoint = new EndpointImpl(path);
        endpoint.httpOperation(method);
        endpoints.add(endpoint);
        return endpoint;
    }

    @Override
    public Scim scim() {
        if (scim == null) {
            scim = new ScimImpl();
        }
        return scim;
    }

    @Override
    protected Collection<DeleteOperationHandler> collectHandlers() {
        var handlers = new ArrayList<DeleteOperationHandler>();
        if (scim != null && scim.isEnabled()) {
            handlers.add(new ScimDeleteHandler(parent.getObjectClass().objectClass(), ((RestConnectorContext) parent.context).scim()));
        }
        for (EndpointImpl endpoint : endpoints) {
            handlers.add(endpoint.build());
        }
        return handlers;
    }

    private class ScimImpl implements Scim {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }
    }

    private class EndpointImpl extends AbstractSingleObjectEndpointBuilder<Uid, Void, EndpointImpl> implements Endpoint {

        private final RequestBuilderImpl request = new RequestBuilderImpl();
        private final ResponseBuilderImpl response = new ResponseBuilderImpl();

        EndpointImpl(String path) {
            super(path);
        }

        @Override
        public EndpointImpl self() {
            return this;
        }

        @Override
        public RequestBuilderImpl request() {
            return request;
        }

        @Override
        public ResponseBuilderImpl response() {
            return response;
        }

        DeleteOperationHandler build() {
            return new EndpointHandler((RestConnectorContext) parent.context, path, httpMethod);
        }
    }

    private static class RequestBuilderImpl extends DeclarativeRequestBuilder<Uid> implements EndpointBuilder.RequestBuilder<Uid> {
        @Override
        public EndpointBuilder.RequestBuilder<Uid> body(Closure<byte[]> bodyTransformer) {
            throw new UnsupportedOperationException(
                    "Custom request bodies are not supported for delete endpoints");
        }
    }

    private static class ResponseBuilderImpl extends DeclarativeResponseBuilder<Void> implements EndpointBuilder.ResponseBuilder<Void> {
    }

    record EndpointHandler(RestConnectorContext context, String path, HttpMethod method) implements DeleteOperationHandler {
        @Override
        public void delete(Uid uid, OperationOptions options, ContextLookup operationContext) {
            var request = context.rest().newRequest();
            request.apiEndpoint(path);
            request.httpMethod(method != null ? method : HttpMethod.DELETE);
            request.pathParameter("id", uid.getUidValue());
            try {
                var response = context.rest().executeRequest(request, new JacksonBodyHandler<>(ObjectNode.class, "endpoint " + path));
                int status = response.statusCode();
                if (status < 200 || status >= 300) {
                    // A 404 becomes UnknownUidException, so deleting an already-deleted object
                    // is idempotent; the full URI (including the UID) is in the message.
                    throw HttpStatusMapper.map(status, HttpStatusMapper.OperationKind.DELETE,
                            String.valueOf(response.request().uri()), uid.getUidValue(), ErrorDetail.extract(response.body()));
                }
            } catch (ConnectorException e) {
                // ICF type was already set at the boundary (mapped status error, mapped
                // network failure) — never re-wrap it (that used to strip the 404 type).
                throw e;
            } catch (InterruptedException e) {
                throw HttpExceptionMapper.map(e, request.getBaseUri() + path);
            } catch (IOException e) {
                // The JDK wraps body-handler errors (e.g. a JSON parse error) into an IOException —
                // surface the original ICF type instead of relabeling it a transient I/O failure.
                var icf = HttpExceptionMapper.unwrapIcf(e);
                if (icf != null) {
                    throw icf;
                }
                throw HttpExceptionMapper.map(e, request.getBaseUri() + path);
            } catch (Exception e) {
                throw new ConnectorException(
                        "Cannot delete object with UID " + uid.getUidValue() + " at endpoint " + path
                                + ": " + HttpExceptionMapper.causeMessage(e), e);
            }
        }
    }
}
