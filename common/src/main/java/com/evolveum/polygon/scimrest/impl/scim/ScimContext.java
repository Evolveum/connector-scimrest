/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.scim;

import com.evolveum.polygon.common.GuardedStringAccessor;
import com.evolveum.polygon.scimrest.ContextLookup;
import com.evolveum.polygon.scimrest.impl.rest.RestContext;
import com.evolveum.polygon.scimrest.RetrievableContext;
import com.evolveum.polygon.scimrest.config.ScimClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.RestHandlerBuilder;
import com.evolveum.polygon.scimrest.schema.RestSchemaBuilder;
import com.unboundid.scim2.client.ScimService;
import com.unboundid.scim2.common.types.SchemaResource;
import com.unboundid.scim2.common.types.ServiceProviderConfigResource;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.ext.RuntimeDelegate;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.glassfish.jersey.client.oauth2.OAuth2ClientSupport;
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

    private ServiceProviderConfigResource providerConfig;

    private Map<String, ScimResourceContext> resources = new HashMap<>();

    private Map<String, SchemaResource> schemas = new HashMap<>();

    private Map<String, String> resourceToObjectClass;
    private Map<String, ScimResourceContext> objectClassToResource;


    public ScimContext(ContextLookup contextLookup, ScimClientConfiguration scimConf) {
        this.contextLookup = contextLookup;
        this.configuration = scimConf;
        var bearerConf = scimConf.supports(ScimClientConfiguration.BearerToken.class);

        var classLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(configuration.getClass().getClassLoader());
            RuntimeDelegate.setInstance(new RuntimeDelegateImpl());
            var clientBuilder = new JerseyClientBuilder();
            if (bearerConf != null && bearerConf.isScimBearerTokenConfigured()) {
                var accessor = new GuardedStringAccessor();
                bearerConf.getScimBearerToken().access(accessor);
                clientBuilder.register(OAuth2ClientSupport.feature(accessor.getClearString()));

                var sslContext = SSLContext.getInstance("SSL");
                sslContext.init(null, new TrustManager[]{RestContext.TRUST_ALL}, new SecureRandom());
                clientBuilder.sslContext(sslContext);
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
        if (endpoint == null) {
            throw new IllegalArgumentException("endpoint is null");
        }
        //Get the base URI from configuration
        URI base = URI.create(configuration.getScimBaseUrl().trim());
        // Fail early if base URL is missing path
        String basePath = base.getPath();
        if (basePath == null || basePath.isEmpty()) {
            throw new IllegalArgumentException("Base URL is missing");
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
                    "Endpoint path: " + epPath + "not under basePath=" + basePath
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
}
