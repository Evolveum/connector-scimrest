package com.evolveum.polygon.scimrest.groovy;

import com.evolveum.polygon.scimrest.JacksonBodyHandler;
import com.evolveum.polygon.scimrest.groovy.api.EndpointBuilder;
import com.evolveum.polygon.scimrest.groovy.api.GroovyContentTypeMixin;
import com.evolveum.polygon.scimrest.groovy.api.HttpMethod;
import com.evolveum.polygon.scimrest.groovy.api.RestCreateOperationBuilder;
import com.evolveum.polygon.scimrest.impl.CreateOperationHandler;
import com.evolveum.polygon.scimrest.impl.CreateOperationStrategyHandler;
import com.evolveum.polygon.scimrest.schema.JsonAttributeMapping;
import com.evolveum.polygon.scimrest.schema.MappedAttribute;
import com.evolveum.polygon.scimrest.schema.MappedObjectClass;
import com.evolveum.polygon.scimrest.spi.CreateOperation;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import groovy.lang.Closure;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.OperationOptions;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

public class RestCreateOperationBuilderImpl implements RestObjectOperationBuilder<CreateOperation>, RestCreateOperationBuilder {


    private final List<EndpointImpl> endpoints = new ArrayList<>();
    private final BaseOperationSupportBuilder parent    ;

    public RestCreateOperationBuilderImpl(BaseOperationSupportBuilder parent) {
        this.parent = parent;
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
    public CreateOperation build() {
        if (endpoints.isEmpty()) {
            return null;
        }
        var handlers = endpoints.stream().map(EndpointImpl::build).toList();

        return new CreateOperationStrategyHandler(parent.context, parent.getObjectClass().objectClass(), handlers);
    }

    private class EndpointImpl extends AbstractSingleObjectEndpointBuilder<Set<Attribute>, ConnectorObject, EndpointImpl> implements Endpoint {

        private RequestBuilderImpl request = new RequestBuilderImpl();
        private ResponseBuilderImpl response = new ResponseBuilderImpl();

        EndpointImpl(String path) {
            super(path);
        }

        @Override
        protected EndpointImpl self() {
            return this;
        }

        CreateOperationHandler build() {
            var supportedAttrs = new HashMap<String, AttributeSupport>();

            for (var supported : supportedAttributes.entrySet()) {
                var attr = resolveAttribute(supported.getKey());
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
                    supportedAttrs
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
            throw new UnsupportedOperationException();
        }
    }

    private static class ResponseBuilderImpl extends DeclarativeResponseBuilder<ConnectorObject> implements EndpointBuilder.ResponseBuilder<ConnectorObject> {

    }

    private MappedAttribute resolveAttribute(String key) {
        // FIXME: Perform checks and throw error if incorrect
        return parent.getObjectClass().attributeFromProtocolName(key);
    }

    record EndpointHandler(ConnectorContext context, String path, String contentType,
                           HttpMethod method,
                           Function<? super Set<Attribute>, byte[]> requestBody,
                           Function<HttpResponse<?>, ConnectorObject> responseHandler,
                           Map<String, AttributeSupport> supportedAttributes) implements CreateOperationHandler {


        @Override
        public Result create(Set<Attribute> createAttributes, OperationOptions options) {
            var request = context.rest().newAuthorizedRequest();
            request.apiEndpoint(path);
            request.httpMethod(method);
            if (contentType != null ) {
                request.header("Content-Type", contentType);
                request.body(requestBody.apply(createAttributes));
            }
            try {
                var response = context.rest().executeRequest(request, new JacksonBodyHandler<>(ObjectNode.class));
                var result = responseHandler.apply(response);
                return new Result(result.getObjectClass(), result.getUid(), result);
            } catch (Exception e) {
                throw new ConnectorException("Cannot create request object", e);
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

    private record DefaultSerializationTransformer(MappedObjectClass schema, HashMap<String, AttributeSupport> supportedAttrs) implements Function<Set<Attribute>, byte[]> {

        public static final JsonNodeFactory FACTORY = new JsonNodeFactory(false);

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

    private record DefaultResponseHandler(MappedObjectClass objectClass) implements Function<HttpResponse<?>, ConnectorObject> {

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
}
