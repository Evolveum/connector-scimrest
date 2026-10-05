/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.scimrest.groovy.operation;

import com.evolveum.polygon.scimrest.groovy.endpoint.DeclarativeResponseBuilder;
import com.evolveum.polygon.scimrest.groovy.endpoint.DeclarativeRequestBuilder;
import com.evolveum.polygon.scimrest.groovy.endpoint.AbstractSingleObjectEndpointBuilder;
import com.evolveum.polygon.scimrest.groovy.schema.BaseOperationSupportBuilder;
import com.evolveum.polygon.scimrest.groovy.connector.RestConnectorContext;

import com.evolveum.polygon.conndev.api.AttributeSupport;
import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.groovy.AbstractCreateOperationBuilder;
import com.evolveum.polygon.conndev.json.JsonAttributeMapping;
import com.evolveum.polygon.scimrest.JacksonBodyHandler;
import com.evolveum.polygon.scimrest.groovy.api.EndpointBuilder;
import com.evolveum.polygon.scimrest.groovy.api.GroovyContentTypeMixin;
import com.evolveum.polygon.scimrest.groovy.api.HttpMethod;
import com.evolveum.polygon.scimrest.groovy.api.RestCreateOperationBuilder;
import com.evolveum.polygon.scimrest.groovy.api.scim.ScimCreateBuilder;
import com.evolveum.polygon.conndev.spi.CreateOperationHandler;
import com.evolveum.polygon.scimrest.impl.scim.ScimCreateHandler;
import com.evolveum.polygon.scimrest.schema.RestAttributeDefinition;
import com.evolveum.polygon.scimrest.schema.RestObjectClassDefinition;
import org.identityconnectors.framework.common.objects.ObjectClass;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import com.evolveum.polygon.scimrest.impl.rest.ErrorDetail;
import com.evolveum.polygon.scimrest.impl.rest.HttpExceptionMapper;
import com.evolveum.polygon.scimrest.impl.rest.HttpStatusMapper;
import groovy.lang.Closure;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

public class RestCreateOperationBuilderImpl extends AbstractCreateOperationBuilder<RestObjectClassDefinition>
        implements RestCreateOperationBuilder {

    private final List<EndpointImpl> endpoints = new ArrayList<>();
    private ScimCreateBuilder scim;

    public RestCreateOperationBuilderImpl(BaseOperationSupportBuilder parent) {
        super(parent);
    }

    @Override
    public Endpoint endpoint(HttpMethod method, String path) {
        for (EndpointImpl endpoint : endpoints) {
            if (endpoint.matches(method, path)) {
                return endpoint;
            }
        }
        var endpoint = new EndpointImpl(path, parent.getObjectClass().objectClass());
        endpoint.httpOperation(method);
        endpoints.add(endpoint);
        return endpoint;
    }

    @Override
    public ScimCreateBuilder scim() {
        if (scim == null) {
            scim = new ScimCreateBuilderImpl();
        }
        return scim;
    }

    @Override
    protected Collection<CreateOperationHandler> collectHandlers() {
        var handlers = new ArrayList<CreateOperationHandler>(endpoints.stream().map(EndpointImpl::build).toList());

        // Add SCIM handler if configured
        if (scim != null && scim.isEnabled()) {
            handlers.add(new ScimCreateHandler(parent.getObjectClass().objectClass(),
                    ((RestConnectorContext) parent.context).scim()));
        }

        return handlers;
    }

    private class EndpointImpl extends AbstractSingleObjectEndpointBuilder<Set<Attribute>, ConnectorObject, EndpointImpl> implements Endpoint {

        private RequestBuilderImpl request = new RequestBuilderImpl();
        private ResponseBuilderImpl response = new ResponseBuilderImpl();
        private AttributeSupport.SupportBuilder<Endpoint> supportedAttributes = new AttributeSupport.SupportBuilder<Endpoint>(this);
        private final ObjectClass objectClass;

        EndpointImpl(String path, ObjectClass objectClass) {
            super(path);
            this.objectClass = objectClass;
        }

        @Override
        public EndpointImpl self() {
            return this;
        }

        @Override
        public AttributeSupport.Builder supportedAttribute(String attributeName) {
            // Fail fast at configuration time when the name does not reference a defined attribute
            resolveAttribute(attributeName, path);
            return supportedAttributes.supportedAttribute(attributeName);
        }

        CreateOperationHandler build() {
            var supportedAttrs = new HashMap<String, AttributeSupport>();

            for (var supported : supportedAttributes.entries()) {
                var attr = resolveAttribute(supported.getKey(), path);
                supportedAttrs.put(attr.connId().getName(), supported.getValue().build(attr));
            }

            // FIXME: Add support for headers

            if (GroovyContentTypeMixin.APPLICATION_JSON.equals(request.contentType) && request.bodyTransformer == null) {
                request.bodyTransformer = new DefaultSerializationTransformer(parent.getObjectClass(), supportedAttrs);
            }

            if (request.contentType != null && request.bodyTransformer == null) {
                throw new ConnectorException("Content type was specified, but missing implementation of body method");
            }

            return new EndpointHandler((RestConnectorContext) parent.context,
                    objectClass,
                    path,
                    request.contentType,
                    httpMethod,
                    request.bodyTransformer,
                    parent.getObjectClass(),
                    supportedAttrs,
                    request.queryParameters
            );
        }

        @Override
        public RequestBuilderImpl request() {
            return request;
        }

        @Override
        public ResponseBuilderImpl response() {
            return response;
        }
    }

    private static class RequestBuilderImpl extends DeclarativeRequestBuilder<Set<Attribute>> implements EndpointBuilder.RequestBuilder<Set<Attribute>> {

        @Override
        public EndpointBuilder.RequestBuilder<Set<Attribute>> body(Closure<byte[]> bodyTransformer) {
            // FIXME: Allow custom implementation
            throw new UnsupportedOperationException(
                    "Custom request bodies are not supported for create endpoints — the body is built from the supported attributes");
        }
    }

    private static class ResponseBuilderImpl extends DeclarativeResponseBuilder<ConnectorObject> implements EndpointBuilder.ResponseBuilder<ConnectorObject> {

    }

    private RestAttributeDefinition resolveAttribute(String key, String endpointPath) {
        return parent.getObjectClass().requireAttribute(
                key, "when defining supported attributes for create endpoint '" + endpointPath + "'");
    }

    record EndpointHandler(RestConnectorContext context,ObjectClass objectClass, String path, String contentType,
                           HttpMethod method,
                           Function<? super Set<Attribute>, byte[]> requestBody,
                           RestObjectClassDefinition objectClass,
                           Map<String, AttributeSupport> supportedAttributes,
                           Map<String, Object> queryParameters) implements CreateOperationHandler {

        @Override
        public Result create(
                Set<Attribute> createAttributes, OperationOptions options, ContextLookup operationContext) {
            var converter = context.lookupConverter();
            var effective = converter != null ? converter.toNative(objectClass, createAttributes) : createAttributes;
            var request = context.rest().newRequest();
            request.apiEndpoint(path);
            request.httpMethod(method);
            queryParameters.forEach(request::queryParameter);
            if (contentType != null ) {
                request.header("Content-Type", contentType);
                request.body(requestBody.apply(effective));
            }
            try {
                var response = context.rest().executeRequest(request, new JacksonBodyHandler<>(ObjectNode.class, "endpoint " + path));
                int status = response.statusCode();
                if (status < 200 || status >= 300) {
                    throw HttpStatusMapper.map(status, HttpStatusMapper.OperationKind.CREATE,
                            String.valueOf(response.request().uri()), null, ErrorDetail.extract(response.body()));
                }
                var body = response.body() instanceof ObjectNode node && !node.isEmpty() ? node : null;
                if (body != null) {
                    var builder = objectClass.newObjectBuilder();
                    for (var attributeDef : objectClass.attributes()) {
                        var valueMapping = attributeDef.mapping(JsonAttributeMapping.class);
                        if (valueMapping != null) {
                            Object connIdValues = valueMapping.valuesFromObject(body);
                            if (connIdValues != null) {
                                builder.addAttribute(attributeDef.attributeOf(connIdValues));
                            }
                        }
                    }
                    var created = builder.build();
                    Uid createdUid;
                    try {
                        createdUid = created.getUid();
                    } catch (IllegalArgumentException e) {
                        // The body has attributes but no UID — the created object cannot be
                        // identified, which breaks the ICF create contract.
                        throw new ConnectorException("Create response at endpoint " + path
                                + " (HTTP " + status + ") does not contain a UID");
                    }
                    return new Result(objectClass.objectClass(), createdUid, created);
                }
                // A 2xx without a JSON body (201/204 without a body) — the object was created
                // but the response does not carry it; the new UID can only come from the
                // Location header.
                var createdUid = uidFromLocationHeader(response);
                if (createdUid == null) {
                    throw new ConnectorException("Create at endpoint " + path
                            + " succeeded (HTTP " + status + ") but the response contains neither"
                            + " an object body nor a Location header; cannot determine the UID"
                            + " of the created object");
                }
                var builder = objectClass.newObjectBuilder();
                for (var attribute : createAttributes) {
                    builder.addAttribute(attribute);
                }
                builder.setUid(createdUid);
                return new Result(objectClass.objectClass(), createdUid, builder.build());
            } catch (ConnectorException e) {
                // ICF type was already set at the boundary (mapped status error, parse error,
                // mapped network failure) — never re-wrap or relabel it.
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
                        "Cannot create object at endpoint " + path + ": " + HttpExceptionMapper.causeMessage(e), e);
            }
        }

        /** Extracts the new UID from the {@code Location} response header, or {@code null}. */
        private static Uid uidFromLocationHeader(HttpResponse<?> response) {
            var location = response.headers().firstValue("Location").orElse(null);
            if (location == null || location.isBlank()) {
                return null;
            }
            try {
                var path = URI.create(location.trim()).getPath();
                if (path == null) {
                    return null;
                }
                var segments = path.endsWith("/") ? path.substring(0, path.length() - 1).split("/")
                        : path.split("/");
                var last = segments[segments.length - 1];
                return last.isBlank() ? null : new Uid(last);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        @Override
        public Capability<Attribute, CreateOperationHandler> canHandle(Collection<Attribute> request, OperationOptions options) {
            if (supportedAttributes.isEmpty()) {
                return new Capability<>(this, request);
            }
            var handled = new ArrayList<Attribute>();
            for (var attr : request) {
                var support = supportedAttributes.get(attr.getName());
                if (support != null && support.isSupported(attr)) {
                    handled.add(attr);
                }
            }
            return new Capability<>(this, handled);
        }
    }

    private record DefaultSerializationTransformer(RestObjectClassDefinition schema, HashMap<String, AttributeSupport> supportedAttrs) implements Function<Set<Attribute>, byte[]> {

        public static final JsonNodeFactory FACTORY = new JsonNodeFactory();

        @Override
        public byte[] apply(Set<Attribute> attributes) {
            var obj = FACTORY.objectNode();
            for (Attribute attr : attributes) {
                var definition = schema.attributeFromConnIdName(attr.getName());
                if (definition == null) {
                    throw new IllegalArgumentException("Unknown attribute: " + attr.getName());
                }

                definition.json().toJsonNode(attr, obj);
            }
            return obj.toPrettyString().getBytes(StandardCharsets.UTF_8);
        }
    }

    private class ScimCreateBuilderImpl implements ScimCreateBuilder {

        boolean enabled = true;

        @Override
        public AttributeValueFilter<AttributeLimitations> supportedAttribute(String attributeName) {
            return null;
        }

        @Override
        public ScimCreateBuilder enabled(boolean value) {
            this.enabled = value;
            return this;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public Limitations limitations() {
            return null;
        }
    }
}
