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
import com.evolveum.polygon.scimrest.groovy.handler.RestHandlerBuilder;
import com.evolveum.polygon.conndev.dev.ConnDevAttribute;
import com.evolveum.polygon.conndev.dev.ConnDevObjectClass;
import com.evolveum.polygon.conndev.dev.ConnDevSchema;
import com.evolveum.polygon.scimrest.impl.scim.dev.ScimDevelopmentMode;
import com.evolveum.polygon.scimrest.impl.scim.dev.ScimObjectClassDevHandler;
import com.evolveum.polygon.scimrest.impl.scim.dev.ScimResourceDevHandler;
import com.evolveum.polygon.scimrest.impl.scim.dev.ScimSchemaDevHandler;
import com.evolveum.polygon.scimrest.impl.scim.dev.ScimServiceProviderConfigDevHandler;
import com.evolveum.polygon.scimrest.schema.RestSchemaBuilderImpl;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.unboundid.scim2.client.ScimService;
import com.unboundid.scim2.common.types.SchemaResource;
import com.unboundid.scim2.common.utils.JsonUtils;
import com.unboundid.scim2.common.utils.MapperFactory;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.RuntimeDelegate;
import com.evolveum.polygon.scimrest.impl.rest.HttpExceptionMapper;
import com.evolveum.polygon.scimrest.impl.rest.HttpStatusMapper;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.glassfish.jersey.internal.RuntimeDelegateImpl;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
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

    private static final Log LOG = Log.getLog(ScimContext.class);

    static {
        // Real SCIM servers commonly send `null` (or omit) boolean AttributeDefinition fields such
        // as `caseExact`; the SDK models them as primitive `boolean`, and Jackson 3 (unlike Jackson 2)
        // defaults to rejecting null-into-primitive instead of coercing it to false.
        //
        // GenericScimObjectDeserializer also reads each ListResponse.Resources element via
        // ObjectReader.readValue(JsonParser) on a shared, mid-stream parser; Jackson 3's default
        // FAIL_ON_TRAILING_TOKENS then misfires whenever more content follows in that stream (e.g.
        // a second resource in the array), rejecting perfectly valid multi-result responses.
        JsonUtils.setCustomMapperFactory(new MapperFactory() {
            @Override
            public JsonMapper.Builder createBuilder() {
                return super.createBuilder()
                        .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                        .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
            }
        });
    }

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
            clientBuilder.register(new ScimSchemaDefaultsFilter());
            clientBuilder.register(new ScimProtocolLogFilter());
            if (authentication != null) {
                clientBuilder.register(new JerseyRequestCustomizerFilter(authentication, scimConf));
            }
            // Route requests through Apache HttpClient 5, which (unlike the JDK HttpURLConnection)
            // supports the PATCH method required by the SCIM PATCH update strategy.
            clientBuilder.property("jersey.config.client.connector.provider", ScimApacheConnectorProvider.class.getName());
            this.httpClient = clientBuilder.build();
            this.scimClient = new ScimService(httpClient.target(scimConf.getScimBaseUrl()));
        } catch (Exception e) {
            // A bad base URL, an unreachable class or an SSL initialization failure all make
            // the connector unusable by configuration — not by transient state.
            throw new ConfigurationException(
                    "Failed to initialize SCIM client at " + scimConf.getScimBaseUrl()
                            + ": " + HttpExceptionMapper.causeMessage(e), e);
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
                // Swallowed on purpose (dev-mode only) — but log it, otherwise a network
                // outage and a missing endpoint are indistinguishable in the dev export.
                LOG.info("Failed to fetch /ServiceProviderConfig (dev mode): {0}", HttpExceptionMapper.causeMessage(e));
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

        } catch (ScimHttpErrorException e) {
            if (e.status() == 404) {
                // The /Schemas or /ResourceTypes endpoint does not exist — the SCIM base URL
                // is misconfigured (retries will not fix it).
                throw new ConfigurationException(
                        "SCIM discovery failed: " + e.requestUri() + " returned 404 — check the SCIM base URL", e);
            }
            // 401/403 -> InvalidCredentialException/PermissionDeniedException, 5xx -> transient,
            // with the server's RFC 7644 detail in the message.
            throw ScimExceptionMapper.map(e, HttpStatusMapper.OperationKind.GET, null);
        } catch (ConnectorException e) {
            // ICF type was already set at the boundary (e.g. mapped network failure) — never re-wrap.
            throw e;
        } catch (Exception e) {
            throw ScimExceptionMapper.mapFailure(e, configuration.getScimBaseUrl());
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
            throw new ConfigurationException("SCIM resource endpoint is null — check the resource type endpoint data");
        }
        //Get the base URI from configuration
        String baseUrl = configuration.getScimBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new ConfigurationException("SCIM base URL is not configured");
        }
        URI base;
        try {
            base = URI.create(baseUrl.trim());
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException("SCIM base URL '" + baseUrl + "' is not a valid URI", e);
        }
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
                throw new ConfigurationException(
                        "SCIM resource endpoint is blank or root: " + endpoint + " (check the resource type endpoint data)");
            }
            return path.startsWith("/") ? path : "/" + path;
        }

        //2) Endpoint is absolute URL; verify it is under base URL
        String epPath = endpoint.getPath();
        if (epPath == null || epPath.isEmpty()) {
            throw new ConfigurationException(
                    "SCIM resource endpoint has an empty path: " + endpoint + " (check the resource type endpoint data)");
        }

        if (!epPath.startsWith(basePath)) {
            throw new ConfigurationException(
                    "SCIM resource endpoint path: " + epPath + " is not under the configured base URL path: " + basePath
            );
        }

        String rel = epPath.substring(basePath.length()); // e.g. "Users"
        if (rel.isEmpty()) {
            return "/";
        }
        return rel.startsWith("/") ? rel : "/" + rel;
    }

    /**
     * Populates the given schema builder from the discovered SCIM resources.
     *
     * @return the translator used, so the caller can apply its rules before {@code build()}
     */
    public ScimSchemaTranslator contributeToSchema(RestSchemaBuilderImpl schemaBuilder) {
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
        return translator;
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

    /** The configured SCIM base URL (e.g. {@code http://host:port/scim}); relative resource endpoints resolve against it. */
    public String scimBaseUrl() {
        return configuration.getScimBaseUrl();
    }

    public Client httpClient() {
        return httpClient;
    }

    public ScimResourceContext resourceForObjectClass(ObjectClass objectClass) {
        return objectClassToResource.get(objectClass.getObjectClassValue());
    }

    public ObjectClass objectClassFromUri(String ref) {
        URI refUri;
        try {
            refUri = URI.create(ref);
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException("Cannot parse SCIM resource reference '" + ref + "': " + HttpExceptionMapper.causeMessage(e), e);
        }
        var uri = relativeEndpoint(refUri);
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
