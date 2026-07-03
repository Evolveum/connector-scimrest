/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.scim;

import com.evolveum.polygon.scimrest.ContextLookup;
import com.evolveum.polygon.scimrest.api.AuthorizationCustomizer;
import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.impl.rest.RestContext;
import com.evolveum.polygon.scimrest.RetrievableContext;
import com.evolveum.polygon.scimrest.config.ScimClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.RestHandlerBuilder;
import com.evolveum.polygon.conndev.dev.ConnDevObjectClass;
import com.evolveum.polygon.conndev.dev.ConnDevSchema;
import com.evolveum.polygon.scimrest.impl.scim.dev.ScimObjectClassDevHandler;
import com.evolveum.polygon.scimrest.schema.RestSchemaBuilder;
import com.unboundid.scim2.client.ScimService;
import com.unboundid.scim2.common.types.SchemaResource;
import com.unboundid.scim2.common.types.ServiceProviderConfigResource;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.ext.RuntimeDelegate;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.glassfish.jersey.internal.RuntimeDelegateImpl;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.ObjectClass;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.net.URI;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

public class ScimContext implements RetrievableContext {

    private final ContextLookup contextLookup;
    private final ScimService scimClient;
    private final Client httpClient;
    private final ScimClientConfiguration configuration;
    private final boolean developmentMode;

    private ServiceProviderConfigResource providerConfig;

    private Map<String, ScimResourceContext> resources = new HashMap<>();

    private Map<String, SchemaResource> schemas = new HashMap<>();

    private Map<String, String> resourceToObjectClass;
    private Map<String, ScimResourceContext> objectClassToResource;


    public ScimContext(ContextLookup contextLookup, ScimClientConfiguration scimConf, boolean developmentMode,
                       AuthorizationCustomizer<ScimClientConfiguration> authentication) {
        this.contextLookup = contextLookup;
        this.configuration = scimConf;
        this.developmentMode = developmentMode;

        var classLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(configuration.getClass().getClassLoader());
            RuntimeDelegate.setInstance(new RuntimeDelegateImpl());
            var clientBuilder = new JerseyClientBuilder();
            // FIXME: ScimClientConfiguration should have getTrustAllCertificates() directly once
            //  BaseScimGroovyConnectorConfiguration is introduced as a parallel to BaseRestGroovyConnectorConfiguration
            if (scimConf instanceof RestClientConfiguration restConf && Boolean.TRUE.equals(restConf.getTrustAllCertificates())) {
                var sslContext = SSLContext.getInstance("SSL");
                sslContext.init(null, new TrustManager[]{RestContext.TRUST_ALL}, new SecureRandom());
                clientBuilder.sslContext(sslContext);
            }
            clientBuilder.register(new ScimHttpErrorFilter());
            if (authentication != null) {
                clientBuilder.register(new JerseyRequestCustomizerFilter(authentication, scimConf));
            }
            this.httpClient = clientBuilder.build();
            this.scimClient = new ScimService(httpClient.target(scimConf.getScimBaseUrl()));
        } catch (Exception e) {
            throw new ConnectorException(e);
        } finally {
            Thread.currentThread().setContextClassLoader(classLoader);
        }
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
                var schemaExtensions = resource.getSchemaExtensions();
                if (schemaExtensions != null) {
                    for (var ext : schemaExtensions) {
                        extensions.put(ext.getSchema().toString(), schemas.get(ext.getSchema().toString()));
                    }
                }
                // SCIM ResourceType.id is optional and Keycloak omits it, so getId() is null for every
                // resource and collapses them into one map entry. The name is required and unique.
                resources.put(resource.getName(), new ScimResourceContext(resource, relativeEndpoint, primary, extensions));
            }

        } catch (Exception e) {
            // FIXME: Throw correct connector exceptions
            throw new ConnectorException(e);
        }

    }

    /**
     * Creates relative URI from absolute URI based on {@link ScimClientConfiguration#getScimBaseUrl()}
     *
     * @param endpoint
     * @return
     */
    private String relativeEndpoint(URI endpoint) {
        if (endpoint == null) {
            throw new IllegalArgumentException("endpoint is null");
        }
        //Get the base URI from configuration
        URI base = URI.create(configuration.getScimBaseUrl().trim());
        // Fail early if base URL is missing path
        String basePath = base.getPath();
        if (basePath == null) {
            basePath = "";
        }
        if (!basePath.endsWith("/")) {
            basePath = basePath + "/";
        }

        //1) Endpoint is returned relative to base URL (conforming to the RFC: https://datatracker.ietf.org/doc/html/rfc7643#section-6)
        if (!endpoint.isAbsolute() && endpoint.getHost() == null) {
            String path = endpoint.getPath();
            if (path == null || path.isBlank() || path.equals("/")) {
                throw new IllegalArgumentException("Endpoint is blank or root: " + endpoint);
            }
            return path.startsWith("/") ? path : "/" + path;
        }

        //2) Endpoint is absolute URL; verify it is under base URL
        String epPath = endpoint.getPath();
        if (epPath == null || epPath.isEmpty()) {
            throw new IllegalArgumentException("Absolute endpoint has empty path: " + endpoint);
        }

        if (!epPath.startsWith(basePath)) {
            throw new IllegalArgumentException(
                    "Endpoint path: " + epPath + " not under basePath=" + basePath
            );
        }

        String rel = epPath.substring(basePath.length()); // e.g. "Users"
        if (rel.isEmpty()) {
            return "/";
        }
        return rel.startsWith("/") ? rel : "/" + rel;
    }

    public void contributeToSchema(RestSchemaBuilder schemaBuilder) {
        var translator = new ScimSchemaTranslator(contextLookup);
        for (var resource : resources.values()) {
            translator.correlateObjectClasses(resource, schemaBuilder);
        }
        resourceToObjectClass = translator.resourceToObjectClass();
        objectClassToResource = translator.objectClassToResource();
        for (var resource : resources.values()) {
            translator.populateSchema(resource, schemaBuilder);
        }
        
        // Contribute the shared conndev dev object classes if developmentMode is enabled
        if (developmentMode) {
            for (var info : ConnDevSchema.objectClassInfos()) {
                schemaBuilder.defineObjectClass(info);
            }
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

            // make sure scim create, update and delete builders are initalized if scripts did not modify them
            ocBuilder.create().scim();
            ocBuilder.update().scim();
            ocBuilder.delete().scim();
        }
        
        // Contribute dev handlers if developmentMode is enabled
        if (developmentMode) {
            contributeDevHandlers(handlerBuilder);
        }
    }

    public void contributeDevHandlers(RestHandlerBuilder handlerBuilder) {
        handlerBuilder.objectClass(ConnDevObjectClass.OBJECT_CLASS_NAME)
                .search(new ScimObjectClassDevHandler(this));
    }

    public ScimService scimClient() {
        return scimClient;
    }

    public Client httpClient() {
        return httpClient;
    }

    public ScimResourceContext resourceForObjectClass(ObjectClass objectClass) {
        return objectClassToResource.get(objectClass.getObjectClassValue());
    }

    public ObjectClass objectClassFromUri(String ref) {
        var uri = relativeEndpoint(URI.create(ref));
        for (var resource : resources.values()) {
            if (uri.startsWith(resource.relativeEndpoint())) {
                var objectClass = resourceToObjectClass.get(resource.resource().getName());
                return new ObjectClass(objectClass);
            }
        }
        return null;
    }

    public Map<String, SchemaResource> getSchemas() {
        return schemas;
    }

    public Map<String, ScimResourceContext> getResources() {
        return resources;
    }

     private String objectClassForResource(ScimResourceContext resource) {
         String resourceName = resource.resource().getName();
         return resourceToObjectClass.get(resourceName);
     }

    public ContextLookup contextLookup() {
        return contextLookup;
    }
}
