package com.evolveum.polygon.scimrest.groovy;

import com.evolveum.polygon.scimrest.JacksonBodyHandler;
import com.evolveum.polygon.scimrest.groovy.api.*;
import com.evolveum.polygon.scimrest.groovy.api.scim.ScimUpdateBuilder;
import com.evolveum.polygon.scimrest.impl.UpdateOperationHandler;
import com.evolveum.polygon.scimrest.impl.UpdateOperationStrategyHandler;
import com.evolveum.polygon.scimrest.impl.scim.ScimUpdateHandler;
import com.evolveum.polygon.scimrest.schema.JsonAttributeMapping;
import com.evolveum.polygon.scimrest.schema.MappedAttribute;
import com.evolveum.polygon.scimrest.schema.MappedObjectClass;
import com.evolveum.polygon.scimrest.spi.UpdateOperation;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import groovy.lang.Closure;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.*;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

public class RestUpdateOperationBuilderImpl implements RestUpdateOperationBuilder, RestObjectOperationBuilder<UpdateOperation> {

    private final BaseOperationSupportBuilder parent;
    private final List<EndpointImpl> endpoints = new ArrayList<>();
    private ScimUpdateBuilderImpl scim;

    public RestUpdateOperationBuilderImpl(BaseOperationSupportBuilder parent) {
        this.parent = parent;
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
            this.scim = new ScimUpdateBuilderImpl(parent.context);
        }
        return scim;
    }

    @Override
    public UpdateOperation build() {
        if (endpoints.isEmpty() && scimNotUsed()) {
            return null;
        }
        var handlers = new ArrayList<UpdateOperationHandler>();
        handlers.addAll(endpoints.stream().map(EndpointImpl::build).toList());
        
        // Add SCIM handler if configured
        if (scim != null && scim.isEnabled()) {
            var scimHandler = scim.build();
            if (scimHandler != null) {
                handlers.add(scimHandler);
            }
        }
        
        return new UpdateOperationStrategyHandler(parent.context, parent.getObjectClass().objectClass(), handlers);
    }

    private boolean scimNotUsed() {
        if (scim == null) {
            return true;
        }
        if (!scim.enabled) {
            return true;
        }
        return !(scim.put != null && scim.put.enabled) && !(scim.patch != null && scim.patch.enabled);
    }

    private MappedAttribute resolveAttribute(String key) {
        // FIXME: Perform checks and throw error if incorrect
        return parent.getObjectClass().attributeFromProtocolName(key);
    }

    class EndpointImpl extends AbstractSingleObjectEndpointBuilder<UpdateRequest, ConnectorObject, EndpointImpl> implements Endpoint  {

        private final RequestBuilderImpl request = new RequestBuilderImpl();
        private final ResponseBuilderImpl response = new ResponseBuilderImpl();
        protected final AttributeSupport.SupportBuilder<Endpoint> supportedAttributes = new AttributeSupport.SupportBuilder<>(this);

        public EndpointImpl(String path) {
            super(path);
        }

        @Override
        protected EndpointImpl self() {
            return this;
        }

        @Override
        public AttributeValueFilter supportedAttribute(String attributeName) {
            return supportedAttributes.supportedAttribute(attributeName);
        }

        @Override
        public AttributeValueFilter supportedAttribute(String attributeName, Closure<?> closure) {
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
                var attr = resolveAttribute(supported.getKey());
                if (attr == null) {
                    throw new ConnectorException("Attribute " + supported.getKey() + " not found in schema");
                }
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

            return new EndpointHandler(parent.context,
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
            throw new UnsupportedOperationException();
        }
    }

    private static class ResponseBuilderImpl extends DeclarativeResponseBuilder<Set<AttributeDelta>> implements EndpointBuilder.ResponseBuilder<Set<AttributeDelta>> {

    }

    record EndpointHandler(ConnectorContext context, String path, String contentType,
                           HttpMethod method,
                           Function<? super UpdateRequest, byte[]> requestBody,
                           Function<HttpResponse<?>, ConnectorObject> responseHandler,
                           Map<String, AttributeSupport> supportedAttributes,
                           boolean requiresOriginalState) implements UpdateOperationHandler {


        @Override
        public void update(UpdateRequest updateRequest, OperationOptions options) {
            var request = context.rest().newAuthorizedRequest();
            request.apiEndpoint(path);
            request.httpMethod(method);
            // FIXME: Use proper path parameter computation
            request.pathParameter("id", updateRequest.uid().getUidValue());

            if (contentType != null) {
                request.header("Content-Type", contentType);
                request.body(requestBody.apply(updateRequest));
            }
            try {
                var response = context.rest().executeRequest(request, new JacksonBodyHandler<>(ObjectNode.class));
                var result = responseHandler.apply(response);
                // Here we should compute changed deltas?

                // return new Result(result.getObjectClass(), result.getUid(), result);
            } catch (Exception e) {
                throw new ConnectorException("Cannot create request object", e);
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

    private record DefaultSerializationTransformer(MappedObjectClass schema,
                                                   HashMap<String, AttributeSupport> supportedAttrs) implements Function<UpdateRequest, byte[]> {

        public static final JsonNodeFactory FACTORY = new JsonNodeFactory(false);

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
            return obj.toPrettyString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private record DefaultResponseHandler(
            MappedObjectClass objectClass) implements Function<HttpResponse<?>, ConnectorObject> {

        @Override
        public ConnectorObject apply(HttpResponse<?> httpResponse) {
            if (httpResponse.statusCode() == 200 || httpResponse.statusCode() == 201) {
                var obj = httpResponse.body();
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
            }
            throw new ConnectorException("Cannot create object. HTTP status code: " + httpResponse.statusCode());
        }
    }

    private class ScimUpdateBuilderImpl implements ScimUpdateBuilder {

        public ScimPutBuilder put;
        public ScimPatchBuilder patch;
        private boolean enabled = true;
        private final ConnectorContext context;

        public ScimUpdateBuilderImpl(ConnectorContext context) {
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
            return put;
        }

        @Override
        public Patch patch() {
            return patch;
        }

        protected ScimUpdateHandler build() {
            if (!enabled) {
                return null;
            }
            return new ScimUpdateHandler(parent.getObjectClass().objectClass(), parent.context.scim());
        }

    }



    private class ScimPatchBuilder implements ScimUpdateBuilder.Patch {

        protected boolean enabled;




        @Override
        public ScimUpdateBuilder.AttributeValueFilter supportedAttribute(String attribute) {
            throw new UnsupportedOperationException();
        }

    }

    protected class ScimPutBuilder implements ScimUpdateBuilder.Put {

        protected boolean enabled;

        @Override
        public ScimUpdateBuilder.AttributeValueFilter supportedAttribute(String attribute) {
            throw new UnsupportedOperationException();
        }

    }

}
