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

import com.evolveum.polygon.conndev.concepts.GroovyClosures;

import com.evolveum.polygon.conndev.api.AttributeSupport;
import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.build.api.UpdateOperationBuilder;
import com.evolveum.polygon.conndev.build.api.UpdateOperationBuilder.UpdateRequest;
import com.evolveum.polygon.conndev.groovy.AbstractUpdateOperationBuilder;
import com.evolveum.polygon.conndev.json.JsonAttributeMapping;
import com.evolveum.polygon.scimrest.JacksonBodyHandler;
import com.evolveum.polygon.scimrest.groovy.api.EndpointBuilder;
import com.evolveum.polygon.scimrest.groovy.api.GroovyContentTypeMixin;
import com.evolveum.polygon.scimrest.groovy.api.HttpMethod;
import com.evolveum.polygon.scimrest.groovy.api.RestUpdateOperationBuilder;
import com.evolveum.polygon.scimrest.groovy.api.scim.ScimUpdateBuilder;
import com.evolveum.polygon.conndev.spi.UpdateOperationHandler;
import com.evolveum.polygon.scimrest.impl.scim.ScimPatchOperations;
import com.evolveum.polygon.scimrest.impl.scim.ScimPatchUpdateHandler;
import com.evolveum.polygon.scimrest.impl.scim.ScimPutUpdateHandler;
import com.evolveum.polygon.scimrest.schema.RestAttributeDefinition;
import com.evolveum.polygon.scimrest.schema.RestObjectClassDefinition;
import com.unboundid.scim2.common.messages.PatchOpType;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import com.evolveum.polygon.scimrest.impl.rest.ErrorDetail;
import com.evolveum.polygon.scimrest.impl.rest.HttpExceptionMapper;
import com.evolveum.polygon.scimrest.impl.rest.HttpStatusMapper;
import groovy.lang.Closure;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.*;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

public class RestUpdateOperationBuilderImpl extends AbstractUpdateOperationBuilder<RestObjectClassDefinition>
        implements RestUpdateOperationBuilder {

    private final List<EndpointImpl> endpoints = new ArrayList<>();
    private ScimUpdateBuilderImpl scim;

    public RestUpdateOperationBuilderImpl(BaseOperationSupportBuilder parent) {
        super(parent);
    }

    @Override
    public Endpoint endpoint(HttpMethod method, String path) {
        for (var endpoint : endpoints) {
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
    public ScimUpdateBuilder scim() {
        if (this.scim == null) {
            this.scim = new ScimUpdateBuilderImpl((RestConnectorContext) parent.context);
        }
        return scim;
    }

    @Override
    protected boolean isEmpty() {
        return endpoints.isEmpty() && scimNotUsed();
    }

    @Override
    protected Collection<UpdateOperationHandler> collectHandlers() {
        var handlers = new ArrayList<UpdateOperationHandler>();
        handlers.addAll(endpoints.stream().map(EndpointImpl::build).toList());

        // Add SCIM handler if configured
        if (scim != null && scim.isEnabled()) {
            var scimHandler = scim.build();
            if (scimHandler != null) {
                handlers.add(scimHandler);
            }
        }

        return handlers;
    }

    private boolean scimNotUsed() {
        // SCIM update is active by default (PATCH); it is only skipped when the SCIM block is
        // not present (REST-only object class) or explicitly disabled.
        return scim == null || !scim.isEnabled();
    }

    private RestAttributeDefinition resolveAttribute(String key, String endpointPath) {
        return parent.getObjectClass().requireAttribute(
                key, "when defining supported attributes for update endpoint '" + endpointPath + "'");
    }

    class EndpointImpl extends AbstractSingleObjectEndpointBuilder<UpdateRequest, ConnectorObject, EndpointImpl> implements Endpoint  {

        private final RequestBuilderImpl request = new RequestBuilderImpl();
        private final ResponseBuilderImpl response = new ResponseBuilderImpl();
        protected final AttributeSupport.SupportBuilder<Endpoint> supportedAttributes = new AttributeSupport.SupportBuilder<>(this);

        public EndpointImpl(String path) {
            super(path);
        }

        @Override
        public EndpointImpl self() {
            return this;
        }

        @Override
        public UpdateOperationBuilder.AttributeValueFilter supportedAttribute(String attributeName) {
            // Fail fast at configuration time when the name does not reference a defined attribute
            resolveAttribute(attributeName, path);
            return supportedAttributes.supportedAttribute(attributeName);
        }

        @Override
        public UpdateOperationBuilder.AttributeValueFilter supportedAttribute(String attributeName, Closure<?> closure) {
            var attr = supportedAttribute(attributeName);
            return GroovyClosures.callAndReturnDelegate(closure, attr);
        }

        @Override
        public Endpoint supportedAttributes(String... attributes) {
            for (String attribute: attributes){

                supportedAttribute(attribute);
            }
            return this;
        }

        @Override
        public RequestBuilder<UpdateRequest> request() {
            return request;
        }

        @Override
        public ResponseBuilder<Set<AttributeDelta>> response() {
            return response;
        }

        UpdateOperationHandler build() {
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

            var responseHandler = new DefaultResponseHandler(parent.getObjectClass());

            return new EndpointHandler((RestConnectorContext) parent.context,
                    path,
                    request.contentType,
                    httpMethod,
                    request.bodyTransformer,
                    responseHandler,
                    supportedAttrs,
                    true
            );
        }
    }

    private static class RequestBuilderImpl extends DeclarativeRequestBuilder<UpdateRequest> implements EndpointBuilder.RequestBuilder<UpdateRequest> {

        @Override
        public EndpointBuilder.RequestBuilder<UpdateRequest> body(Closure<byte[]> bodyTransformer) {
            throw new UnsupportedOperationException(
                    "Custom request bodies are not supported for update endpoints — the body is built from the supported attributes");
        }
    }

    private static class ResponseBuilderImpl extends DeclarativeResponseBuilder<Set<AttributeDelta>> implements EndpointBuilder.ResponseBuilder<Set<AttributeDelta>> {

    }

    record EndpointHandler(RestConnectorContext context, String path, String contentType,
                           HttpMethod method,
                           Function<? super UpdateRequest, byte[]> requestBody,
                           Function<HttpResponse<?>, ConnectorObject> responseHandler,
                           Map<String, AttributeSupport> supportedAttributes,
                           boolean requiresOriginalState) implements UpdateOperationHandler {

        @Override
        public void update(
                UpdateRequest updateRequest, OperationOptions options, ContextLookup operationContext) {
            var request = context.rest().newRequest();
            request.apiEndpoint(path);
            request.httpMethod(method);
            // FIXME: Use proper path parameter computation
            request.pathParameter("id", updateRequest.uid().getUidValue());

            if (contentType != null) {
                request.header("Content-Type", contentType);
                request.body(requestBody.apply(updateRequest));
            }
            try {
                var response = context.rest().executeRequest(request, new JacksonBodyHandler<>(ObjectNode.class, "endpoint " + path));
                var result = responseHandler.apply(response);
                // Here we should compute changed deltas?

                // return new Result(result.getObjectClass(), result.getUid(), result);
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
                        "Cannot update object with UID " + updateRequest.uid().getUidValue() + " at endpoint " + path
                                + ": " + HttpExceptionMapper.causeMessage(e), e);
            }
        }

        @Override
        public Capability<AttributeDelta, UpdateOperationHandler> canHandle(Collection<AttributeDelta> request, OperationOptions options) {
            if (supportedAttributes.isEmpty()) {
                return new Capability<>(this, request);
            }
            var handled = new ArrayList<AttributeDelta>();
            for (var attr : request) {
                var support = supportedAttributes.get(attr.getName());
                if (support != null && support.isSupported(attr)) {
                    handled.add(attr);
                }
            }
            return new Capability<>(this, handled);
        }
    }

    private record DefaultSerializationTransformer(RestObjectClassDefinition schema,
                                                   HashMap<String, AttributeSupport> supportedAttrs) implements Function<UpdateRequest, byte[]> {

        public static final JsonNodeFactory FACTORY = new JsonNodeFactory();

        @Override
        public byte[] apply(UpdateRequest request) {
            var obj = FACTORY.objectNode();
            for (var delta : request.attributeDeltaSet()) {
                var definition = schema.attributeFromConnIdName(delta.getName());
                if (definition == null) {
                    throw new IllegalArgumentException("Unknown attribute: " + delta.getName());
                }
                var attrBuilder = new AttributeBuilder();
                attrBuilder.setName(delta.getName());
                Attribute attr = null;
                if (request.before() != null) {
                    attr = request.before().getAttributeByName(delta.getName());
                }
                if (attr == null) {
                    attr = AttributeBuilder.build(delta.getName());
                }
                var updated = delta.applyTo(attr);
                definition.json().toJsonNode(updated, obj);
            }
            return obj.toPrettyString().getBytes(StandardCharsets.UTF_8);
        }
    }

    private record DefaultResponseHandler(
            RestObjectClassDefinition objectClass) implements Function<HttpResponse<?>, ConnectorObject> {

        @Override
        public ConnectorObject apply(HttpResponse<?> httpResponse) {
            int status = httpResponse.statusCode();
            if (status >= 200 && status < 300) {
                var obj = httpResponse.body();
                if (obj instanceof ObjectNode remoteObj && !remoteObj.isEmpty()) {
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
                // A 2xx with an empty or absent body is a successful update — not an error.
                return objectClass.newObjectBuilder().build();
            }
            throw HttpStatusMapper.map(status, HttpStatusMapper.OperationKind.UPDATE,
                    String.valueOf(httpResponse.request().uri()), null, ErrorDetail.extract(httpResponse.body()));
        }
    }

    private class ScimUpdateBuilderImpl implements ScimUpdateBuilder {

        private ScimPutBuilder put;
        private ScimPatchBuilder patch;
        private boolean enabled = true;
        private final RestConnectorContext context;

        public ScimUpdateBuilderImpl(RestConnectorContext context) {
            this.context = context;
        }

        @Override
        public ScimUpdateBuilder enabled(boolean enabled) {
            this.enabled = enabled;
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

        @Override
        public Limitations limitations(Closure<?> closure) {
            return null;
        }

        @Override
        public Put put() {
            if (put == null) {
                put = new ScimPutBuilder();
            }
            return put;
        }

        @Override
        public Patch patch() {
            if (patch == null) {
                patch = new ScimPatchBuilder();
            }
            return patch;
        }

        protected UpdateOperationHandler build() {
            if (!enabled) {
                return null;
            }
            var objectClass = parent.getObjectClass().objectClass();
            var scimContext = ((RestConnectorContext) parent.context).scim();
            if (put != null) {
                var supported = put.supportedAttributes;
                return new ScimPutUpdateHandler(objectClass, scimContext,
                        supported.isEmpty() ? null : supported);
            }
            Set<String> supported = null;
            Map<String, ScimPatchOperations.PatchAttrConfig> patchConfig = null;
            if (patch != null) {
                supported = patch.supportedAttributes;
                patchConfig = buildPatchConfig(patch.perAttribute);
            }
            return new ScimPatchUpdateHandler(objectClass, scimContext,
                    supported != null && supported.isEmpty() ? null : supported, patchConfig);
        }

        private Map<String, ScimPatchOperations.PatchAttrConfig> buildPatchConfig(Map<String, AttrData> perAttribute) {
            if (perAttribute == null || perAttribute.isEmpty()) {
                return null;
            }
            var config = new HashMap<String, ScimPatchOperations.PatchAttrConfig>();
            for (var entry : perAttribute.entrySet()) {
                var data = entry.getValue();
                config.put(entry.getKey(), new ScimPatchOperations.PatchAttrConfig(
                        toPatchOpTypes(data.operations), data.maxPerRequest, data.pinnedValues));
            }
            return config;
        }

        private static Set<PatchOpType> toPatchOpTypes(Set<String> operations) {
            if (operations == null) {
                return null;
            }
            var result = new HashSet<PatchOpType>();
            for (var op : operations) {
                result.add(PatchOpType.valueOf(op.toUpperCase(Locale.ROOT)));
            }
            return result;
        }
    }

    /** Per-attribute SCIM update configuration collected from the {@code supportedAttribute} DSL. */
    private static final class AttrData {
        List<Object> pinnedValues;
        Set<String> operations;
        int maxPerRequest = ScimPatchOperations.PatchAttrConfig.UNLIMITED;
    }

    /**
     * The {@code supportedAttribute(name) { ... }} filter for the SCIM put/patch builders. Records a
     * pinned value ({@code value}) and per-attribute limitations ({@code operations},
     * {@code maxPerRequest}) into the owning attribute's {@link AttrData}.
     */
    private static final class ScimUpdateFilter implements ScimUpdateBuilder.AttributeValueFilter {

        private final AttrData data;
        private final LimitationsImpl limitations;

        ScimUpdateFilter(AttrData data) {
            this.data = data;
            this.limitations = new LimitationsImpl(data);
        }

        @Override
        public UpdateOperationBuilder.AttributeValueFilter value(Object value) {
            if (data.pinnedValues == null) {
                data.pinnedValues = new ArrayList<>();
            }
            data.pinnedValues.add(value);
            return this;
        }

        @Override
        public UpdateOperationBuilder.AttributeValueFilter transition(Object oldValue, Object newValue) {
            return this;
        }

        @Override
        public ScimUpdateBuilder.AttributeLimitations limitations() {
            return limitations;
        }

        @Override
        public ScimUpdateBuilder.AttributeLimitations limitations(Closure<?> definition) {
            return GroovyClosures.callAndReturnDelegate(definition, limitations());
        }

        private static final class LimitationsImpl implements ScimUpdateBuilder.AttributeLimitations {
            private final AttrData data;

            LimitationsImpl(AttrData data) {
                this.data = data;
            }

            @Override
            public ScimUpdateBuilder.AttributeLimitations maxPerRequest(int maximum) {
                data.maxPerRequest = maximum;
                return this;
            }

            @Override
            public ScimUpdateBuilder.AttributeLimitations operations(String... operations) {
                data.operations = new HashSet<>(List.of(operations));
                return this;
            }
        }
    }

    private class ScimPatchBuilder implements ScimUpdateBuilder.Patch {

        final Set<String> supportedAttributes = new LinkedHashSet<>();
        final Map<String, AttrData> perAttribute = new HashMap<>();

        @Override
        public ScimUpdateBuilder.AttributeValueFilter supportedAttribute(String attribute) {
            supportedAttributes.add(attribute);
            return new ScimUpdateFilter(perAttribute.computeIfAbsent(attribute, k -> new AttrData()));
        }
    }

    private class ScimPutBuilder implements ScimUpdateBuilder.Put {

        final Set<String> supportedAttributes = new LinkedHashSet<>();
        final Map<String, AttrData> perAttribute = new HashMap<>();

        @Override
        public ScimUpdateBuilder.AttributeValueFilter supportedAttribute(String attribute) {
            supportedAttributes.add(attribute);
            return new ScimUpdateFilter(perAttribute.computeIfAbsent(attribute, k -> new AttrData()));
        }
    }

}
