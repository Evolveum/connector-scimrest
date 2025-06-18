package com.evolveum.polygon.scim.rest;

import com.evolveum.polygon.common.GuardedStringAccessor;
import com.evolveum.polygon.scim.rest.config.ScimClientConfiguration;
import com.evolveum.polygon.scim.rest.groovy.RestHandlerBuilder;
import com.evolveum.polygon.scim.rest.groovy.ScimSchemaTranslator;
import com.evolveum.polygon.scim.rest.schema.RestSchemaBuilder;
import com.unboundid.scim2.client.ScimService;
import com.unboundid.scim2.common.types.SchemaResource;
import com.unboundid.scim2.common.types.ServiceProviderConfigResource;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import org.glassfish.jersey.client.oauth2.OAuth2ClientSupport;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.ObjectClass;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class ScimContext implements RetrievableContext {


    private final ScimService scimClient;
    private final Client httpClient;
    private final ScimClientConfiguration configuration;

    private ServiceProviderConfigResource providerConfig;

    private Map<String, ScimResourceContext> resources = new HashMap<>();

    private Map<String, SchemaResource> schemas = new HashMap<>();

    private Map<String, String> resourceToObjectClass;
    private Map<String, ScimResourceContext> objectClassToResource;


    public ScimContext(ScimClientConfiguration scimConf) {
        this.configuration = scimConf;
        var bearerConf = scimConf.supports(ScimClientConfiguration.BearerToken.class);

        var clientBuilder = ClientBuilder.newBuilder();
        if (bearerConf != null && bearerConf.isScimBearerTokenConfigured()) {
            var accessor = new GuardedStringAccessor();
            bearerConf.getScimBearerToken().access(accessor);
            clientBuilder.register(OAuth2ClientSupport.feature(accessor.getClearString()));
        }

        this.httpClient = clientBuilder.build();
        this.scimClient = new ScimService(httpClient.target(scimConf.getScimBaseUrl()));
    }

    public void initialize() {
        try {
            //this.providerConfig = scimClient.getServiceProviderConfig();

            for (var schema : scimClient.getSchemas()) {
                schemas.put(schema.getId(), schema);
            }

            for (var resource : scimClient.getResourceTypes()) {
                var primary = schemas.get(resource.getSchema().toString());
                var extensions = new HashMap<String, SchemaResource>();
                var relativeEndpoint = relativeEndpoint(resource.getEndpoint());
                for (var ext : resource.getSchemaExtensions()) {
                    extensions.put(ext.getSchema().toString(), schemas.get(ext.getSchema().toString()));
                }
                resources.put(resource.getId(), new ScimResourceContext(resource, relativeEndpoint, primary, extensions));
            }

        } catch (Exception e) {
            // FIXME: Throw correct connector exceptions
            throw new ConnectorException(e);
        }

    }

    /**
     * Creates relative URI from absolute URI based on {@link ScimClientConfiguration#getScimBaseUrl()}
     *
     *
     *
     * @param endpoint
     * @return
     */
    private String relativeEndpoint(URI endpoint) {
        var endpointStr = endpoint.toString();

        // Drop HTTP // HTTPS prefix from both
        endpointStr = removeProtocol(endpointStr);
        var baseUrl = removeProtocol(configuration.getScimBaseUrl());

        if (endpointStr.startsWith(baseUrl)) {
            return endpointStr.substring(baseUrl.length());
        }
        throw new IllegalArgumentException("Can not relativize endpoint.");
    }

    private String removeProtocol(String endpointStr) {
        return endpointStr.replaceFirst("^https?://", "");
    }

    public void contributeToSchema(RestSchemaBuilder schemaBuilder) {
        var translator = new ScimSchemaTranslator();
        for (var resource : resources.values()) {
            translator.correlateObjectClasses(resource, schemaBuilder);
        }
        resourceToObjectClass = translator.resourceToObjectClass();
        objectClassToResource = translator.objectClassToResource();
        for (var resource : resources.values()) {
            translator.populateSchema(resource, schemaBuilder);
        }
    }

    public void contributeToHandlers(RestHandlerBuilder handlerBuilder) {
        for (var resource : resources.values()) {
            // FIXME: Probably we should have list of resources to ignore
            var ocBuilder = handlerBuilder.objectClass(objectClassForResource(resource));

            var searchBuilder = ocBuilder.searchBuilder();

            //var getManuallyImplemented = searchBuilder.getAlreadyImplemented();

            var scim = searchBuilder.scim();
            if (scim.isEnabled()) {
                // SCIM Configuration
            }
        }
    }

    private String objectClassForResource(ScimResourceContext resource) {
        return resourceToObjectClass.get(resource.resource().getName());
    }

    public ScimService scimClient() {
        return scimClient;
    }

    public ScimResourceContext resourceForObjectClass(ObjectClass objectClass) {
        return objectClassToResource.get(objectClass.getObjectClassValue());
    }
}
