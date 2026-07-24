/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.scim;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.scimrest.api.AuthorizationCustomizer;
import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.impl.rest.RestContext;
import com.evolveum.polygon.conndev.concepts.RetrievableContext;
import com.evolveum.polygon.scimrest.config.ScimClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.RestHandlerBuilder;
import com.evolveum.polygon.conndev.dev.ConnDevAttribute;
import com.evolveum.polygon.conndev.dev.ConnDevObjectClass;
import com.evolveum.polygon.conndev.dev.ConnDevSchema;
import com.evolveum.polygon.scimrest.impl.scim.dev.ScimDevelopmentMode;
import com.evolveum.polygon.scimrest.impl.scim.dev.ScimObjectClassDevHandler;
import com.evolveum.polygon.scimrest.impl.scim.dev.ScimResourceDevHandler;
import com.evolveum.polygon.scimrest.impl.scim.dev.ScimSchemaDevHandler;
import com.evolveum.polygon.scimrest.impl.scim.dev.ScimServiceProviderConfigDevHandler;
import com.evolveum.polygon.scimrest.schema.RestSchemaBuilderImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.unboundid.scim2.client.ScimService;
import com.unboundid.scim2.common.types.SchemaResource;
import com.unboundid.scim2.common.utils.JsonUtils;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.RuntimeDelegate;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.glassfish.jersey.internal.RuntimeDelegateImpl;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.AttributeInfoBuilder;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.ObjectClassInfoBuilder;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.io.IOException;
import java.net.URI;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScimContext implements RetrievableContext {

    private final ContextLookup contextLookup;
    private final ScimService scimClient;
    private final Client httpClient;
    private final ScimClientConfiguration configuration;
    private final boolean developmentMode;

    private JsonNode providerConfig;

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
        if (developmentMode) {
            try {
                // /ServiceProviderConfig is required by RFC 7644, but not every server implements
                // it; the config feeds only the development export, so a failure must not break init
                this.providerConfig = fetchServiceProviderConfig();
            } catch (Exception e) {
                this.providerConfig = null;
            }
        }
        try {
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
     * Fetches /ServiceProviderConfig as raw JSON, bypassing
     * {@link ScimService#getServiceProviderConfig()}: servers commonly return it with
     * {@code Content-Type: application/json}, for which Jersey picks the JSON-B entity provider
     * instead of the SCIM SDK Jackson one, and JSON-B cannot instantiate
     * {@code ServiceProviderConfigResource} (no default constructor). The typed resource is also
     * undesirable here: the SDK rejects non-standard attributes (e.g. cursor {@code pagination}),
     * while the development export should forward the document verbatim.
     */
    private JsonNode fetchServiceProviderConfig() throws IOException {
        var json = httpClient.target(configuration.getScimBaseUrl())
                .path("ServiceProviderConfig")
                .request(ScimService.MEDIA_TYPE_SCIM_TYPE, MediaType.APPLICATION_JSON_TYPE)
                .get(String.class);
        return JsonUtils.getObjectReader().readTree(json);
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

    public void contributeToSchema(RestSchemaBuilderImpl schemaBuilder) {
        var translator = new ScimSchemaTranslator(contextLookup);
        for (var resource : resources.values()) {
            translator.correlateObjectClasses(resource, schemaBuilder);
        }
        resourceToObjectClass = translator.resourceToObjectClass();
        objectClassToResource = translator.objectClassToResource();
        for (var resource : resources.values()) {
            translator.populateSchema(resource, schemaBuilder);
        }
        
        // Contribute the dev object classes if developmentMode is enabled: the shared
        // conndev_ObjectClass (precomputed mapping derived from the schema model) and the raw
        // conndev_ScimSchema/conndev_ScimResource/conndev_ScimServiceProviderConfig export (full
        // /Schemas + /ResourceTypes + /ServiceProviderConfig JSON, which still carries details the
        // model does not map yet — complex attributes, extension schemas, provider capabilities).
        if (developmentMode) {
            var extraObjectClassFields = List.of(ConnDevSchema.embeddedBlock(SCIM_BLOCK, SCIM_BLOCK_TYPE));
            var extraAttributeFields = List.of(ConnDevSchema.embeddedBlock(SCIM_BLOCK, SCIM_ATTRIBUTE_BLOCK_TYPE));
            for (var info : ConnDevSchema.objectClassInfos(extraObjectClassFields, extraAttributeFields)) {
                schemaBuilder.defineObjectClass(info);
            }
            schemaBuilder.defineObjectClass(scimObjectClassBlock());
            schemaBuilder.defineObjectClass(scimAttributeBlock());
            new ScimDevelopmentMode().contributeSchemaObjects(schemaBuilder);
        }
    }

    private static final String SCIM_BLOCK = "scim";
    private static final String SCIM_BLOCK_TYPE = ConnDevObjectClass.protocolBlockType(SCIM_BLOCK);
    private static final String SCIM_ATTRIBUTE_BLOCK_TYPE = ConnDevAttribute.attributeProtocolBlockType(SCIM_BLOCK);

    /** The object-class-level {@code scim} block: SCIM resource name and schema URI. */
    private static ObjectClassInfo scimObjectClassBlock() {
        var builder = new ObjectClassInfoBuilder();
        builder.setType(SCIM_BLOCK_TYPE);
        builder.setEmbedded(true);
        builder.addAttributeInfo(AttributeInfoBuilder.build("name", String.class));
        builder.addAttributeInfo(AttributeInfoBuilder.build("schemaUri", String.class));
        return builder.build();
    }

    /** The attribute-level {@code scim} block: the SCIM JSON path. Distinct ConnId type from the
     *  object-class-level block of the same name - see {@link ConnDevAttribute#attributeProtocolBlockType}. */
    private static ObjectClassInfo scimAttributeBlock() {
        var builder = new ObjectClassInfoBuilder();
        builder.setType(SCIM_ATTRIBUTE_BLOCK_TYPE);
        builder.setEmbedded(true);
        builder.addAttributeInfo(AttributeInfoBuilder.build("path", String.class));
        return builder.build();
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
                .search(new ScimObjectClassDevHandler());
        handlerBuilder.objectClass(ScimDevelopmentMode.SCHEMA_OC_NAME)
                .search(new ScimSchemaDevHandler(this));
        handlerBuilder.objectClass(ScimDevelopmentMode.RESOURCE_OC_NAME)
                .search(new ScimResourceDevHandler(this));
        handlerBuilder.objectClass(ScimDevelopmentMode.SERVICE_PROVIDER_CONFIG_OC_NAME)
                .search(new ScimServiceProviderConfigDevHandler(this));
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

    public JsonNode getServiceProviderConfig() {
        return providerConfig;
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
