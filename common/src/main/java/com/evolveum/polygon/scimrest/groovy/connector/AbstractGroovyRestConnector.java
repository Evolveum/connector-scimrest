/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy.connector;

import com.evolveum.polygon.scimrest.groovy.api.AuthenticationCustomizationBuilder;
import com.evolveum.polygon.scimrest.groovy.api.RestObjectClassSchemaBuilder;
import com.evolveum.polygon.scimrest.groovy.handler.GroovyRestHandlerBuilder;
import com.evolveum.polygon.scimrest.groovy.handler.RestHandlerBuilder;
import com.evolveum.polygon.scimrest.groovy.schema.BaseOperationSupportBuilder;
import com.evolveum.polygon.scimrest.groovy.schema.SchemaDefinitionLoader;
import com.evolveum.polygon.scimrest.yaml.YamlRestHandlerLoader;

import com.evolveum.polygon.conndev.spi.ClassHandlerConnectorBase;
import com.evolveum.polygon.conndev.groovy.BaseGroovyConnectorConfiguration;
import com.evolveum.polygon.conndev.groovy.GroovyScriptValidator;
import com.evolveum.polygon.conndev.groovy.ScriptValidationRequest;
import com.evolveum.polygon.conndev.groovy.ScriptValidationResult;
import com.evolveum.polygon.conndev.spi.ObjectClassHandler;
import com.evolveum.polygon.conndev.yaml.GroovyScriptCompiler;
import com.evolveum.polygon.conndev.yaml.ScriptResources;
import com.evolveum.polygon.conndev.yaml.YamlSchemaLoader;
import com.evolveum.polygon.conndev.yaml.decl.GroovySyntaxChecker;
import com.evolveum.polygon.conndev.yaml.decl.YamlScriptValidator;
import com.evolveum.polygon.scimrest.api.AuthorizationCustomizer;
import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.config.ScimClientConfiguration;
import com.evolveum.polygon.scimrest.impl.rest.HttpStatusMapper;
import com.evolveum.polygon.scimrest.impl.rest.HttpExceptionMapper;
import com.evolveum.polygon.scimrest.impl.scim.ScimExceptionMapper;
import com.evolveum.polygon.scimrest.impl.scim.ScimHttpErrorException;
import com.evolveum.polygon.scimrest.schema.RestSchema;
import com.evolveum.polygon.scimrest.schema.RestSchemaBuilderImpl;
import java.io.InputStreamReader;

import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.exceptions.ConnectionBrokenException;
import org.identityconnectors.framework.common.exceptions.ConnectionFailedException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.InvalidCredentialException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeDelta;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.SyncResultsHandler;
import org.identityconnectors.framework.common.objects.SyncToken;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.spi.Configuration;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.Set;
import java.util.concurrent.Callable;

public abstract class AbstractGroovyRestConnector extends ClassHandlerConnectorBase<RestConnectorContext> {

    private GroovyRestHandlerBuilder handlersBuilder;

    @Deprecated
    protected AbstractGroovyRestConnector() {
        this(true);
    }

    protected AbstractGroovyRestConnector(boolean reinitializeOnEachCall) {
        super(reinitializeOnEachCall);
    }

    @Override
    public BaseGroovyConnectorConfiguration getConfiguration() {
        return context.configuration();
    }

    @Override
    public RestConnectorContext context() {
        return context;
    }

    @Override
    public ObjectClassHandler handlerFor(ObjectClass objectClass) throws UnsupportedOperationException {
        initializeCore();
        initializeHandlers();
        var handler =  context.handlerFor(objectClass);
        if (handler == null) {
            throw new UnsupportedOperationException("Cannot find handler for " + objectClass);
        }
        return handler;
    }

    @Override
    public void init(Configuration cfg) {
        if (cfg instanceof BaseGroovyConnectorConfiguration groovyConf) {
            context = new RestConnectorContext(groovyConf);
        } else {
            throw new ConfigurationException(
                    "Configuration must be an instance of AbstractGroovyConnectorConfiguration, got: "
                            + (cfg == null ? "null" : cfg.getClass().getName()));
        }
    }

    @Override
    public void test() {
        initializeCore();
        // SCIM Test connection is done automatically during schema discovery
        // FIXME: But makes sense to do again, if connector is poolable (in future)
        var restClientConfig = getConfiguration().configuration(RestClientConfiguration.class);
        if (restClientConfig == null || restClientConfig.getRestTestEndpoint() == null) {
            // Nothing to verify: preference-based auth falls back to its first method without
            // probing, and the SCIM connection was already checked during schema discovery.
            return;
        }

        if (context.rest() != null && context.rest().isPreferenceActive()) {
            context.rest().runProbe();
        } else if (context.isScimEnabled()) {
            var scimBase = ((ScimClientConfiguration) getConfiguration()).getScimBaseUrl();
            var testUrl = scimBase + restClientConfig.getRestTestEndpoint();

            try {
                context.scim().httpClient().target(testUrl).request().get().close();
            } catch (ScimHttpErrorException e) {
                // Carries the status + the server's RFC 7644 detail; classify like the REST branch.
                throw testEndpointStatusException(e.status(), e.detail(), testUrl, e);
            } catch (ConnectorException e) {
                // Network failure already mapped at the connector boundary (timeout, connection) — keep it.
                throw e;
            } catch (Exception e) {
                throw ScimExceptionMapper.mapFailure(e, testUrl);
            }
        } else if (context.rest() != null) {
            var testUrl = restClientConfig.getBaseAddress() + restClientConfig.getRestTestEndpoint();
            var request = context.rest().newRequest();
            request.subpath(restClientConfig.getRestTestEndpoint());
            try {
                var response = context.rest().executeRequest(request, HttpResponse.BodyHandlers.discarding());
                if (!isSuccess(response.statusCode())) {
                    throw testEndpointStatusException(response.statusCode(), null, testUrl, null);
                }
            } catch (IOException e) {
                // Timeouts and connection problems become the ICF types midPoint retries
                // (OperationTimeoutException / ConnectionFailedException) instead of a
                // generic wrap.
                throw HttpExceptionMapper.map(e, testUrl);
            } catch (IllegalArgumentException e) {
                // A bad base address is misconfiguration, not a connection problem.
                throw new ConfigurationException("DNS or URI configuration error: " + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ConnectionBrokenException("Test request to " + testUrl + " was interrupted", e);
            }
        }
    }

    @Override
    public Uid create(ObjectClass objectClass, Set<Attribute> createAttributes, OperationOptions options) {
        return withStandardIcfBoundary(() -> super.create(objectClass, createAttributes, options));
    }

    @Override
    public void delete(ObjectClass objectClass, Uid uid, OperationOptions options) {
        withStandardIcfBoundary(() -> {
            super.delete(objectClass, uid, options);
            return null;
        });
    }

    @Override
    public void executeQuery(ObjectClass objectClass, Filter query, ResultsHandler handler, OperationOptions options) {
        withStandardIcfBoundary(() -> {
            super.executeQuery(objectClass, query, handler, options);
            return null;
        });
    }

    @Override
    public Set<AttributeDelta> updateDelta(ObjectClass objclass, Uid uid, Set<AttributeDelta> modifications, OperationOptions options) {
        return withStandardIcfBoundary(() -> super.updateDelta(objclass, uid, modifications, options));
    }

    @Override
    public void sync(ObjectClass objectClass, SyncToken token, SyncResultsHandler handler, OperationOptions options) {
        withStandardIcfBoundary(() -> {
            super.sync(objectClass, token, handler, options);
            return null;
        });
    }

    @Override
    public SyncToken getLatestSyncToken(ObjectClass objectClass) {
        return withStandardIcfBoundary(() -> super.getLatestSyncToken(objectClass));
    }

    @Override
    public Schema schema() {
        initializeCore();
        return context.schema().connIdSchema();
    }

    @Override
    public void dispose() {
        // Dispose of connector
    }

    protected abstract void initializeAuthorizationHandler(GroovyRestHandlerBuilder builder);

    protected AuthorizationCustomizer<RestClientConfiguration> authorizationCustomizer() {
        return (c,v) -> {};
    }

    /**
     * Validates the candidate script against a throwaway target seeded with all currently
     * deployed sibling scripts (via {@link #schemaResources} / {@link #operationResources}, minus
     * {@code filename} itself), so cross-references to them (e.g. a schema attribute's {@code
     * referencedObjectClass}) resolve during evaluation and build, and so the candidate replaces
     * rather than merges with its own old content. For a schema candidate that builds
     * successfully, also re-checks every deployed operation script against the candidate schema
     * (not the currently deployed one), since a schema change can break an operation script that
     * references the changed definitions. A YAML candidate ({@link
     * ScriptValidationRequest#isYaml()}) goes through {@link #validateYamlSchema}/{@link
     * #validateYamlOperations} instead, under the same contract.
     */
    @Override
    protected ScriptValidationResult validateScript(ScriptValidationRequest request) throws Exception {
        if (ScriptValidationRequest.ARTIFACT_KIND_SCHEMA.equals(request.artifactKind())) {
            if (request.isYaml()) {
                return validateYamlSchema(request);
            }
            var builder = new RestSchemaBuilderImpl(getClass(), context);
            var loader = new SchemaDefinitionLoader(context.configuration().groovyContext(), builder);
            schemaResources(request.filename()).forEach(loader::loadFromResource);
            RestSchema[] candidateSchema = new RestSchema[1];
            var schemaResult = GroovyScriptValidator.validate(
                    loader::parse, () -> {
                        builder.applyStructuralRules();
                        candidateSchema[0] = builder.build();
                    }, request.scriptText(), request.operation());
            if (schemaResult.status() != ScriptValidationResult.Status.OK
                    || !ScriptValidationRequest.SCRIPT_OPERATION_BUILD.equals(request.operation())) {
                return schemaResult;
            }
            return validateOperationsAgainstCandidateSchema(candidateSchema[0]);
        }
        if (request.isYaml()) {
            return validateYamlOperations(request);
        }
        initializeCore();
        var builder = new GroovyRestHandlerBuilder(context.configuration().groovyContext(), context);
        operationResources(request.filename()).forEach(builder::loadFromResource);
        return GroovyScriptValidator.validate(builder::parse, builder::build, request.scriptText(), request.operation());
    }

    private void initializeCore() {
        if (reinitializeOnEachCall || !coreInitialized) {
            initializeCore0();
            coreInitialized = true;
            fullyInitialized = false;
        }
    }

    private void initializeHandlers() {
        if (reinitializeOnEachCall || !fullyInitialized) {
            initializeHandlers0();
            fullyInitialized = true;
        }
    }

    private void initializeCore0() {
        try {
            var schemaBuilder = new RestSchemaBuilderImpl(getClass(), context);
            var schemaLoader = new SchemaDefinitionLoader(context.configuration().groovyContext(), schemaBuilder);
            initializeSchema(schemaLoader);

            handlersBuilder = context.handlerBuilder(context.configuration().groovyContext());
            initializeAuthorizationHandler(handlersBuilder);

            context.initializeRest(handlersBuilder.restCustomizer());
            context.initializeScim(handlersBuilder.scimCustomizer());
            if (context.isScimEnabled()) {
                context.scim().initialize();
                context.scim().contributeToSchema(schemaBuilder).applyRules();
            }

            schemaBuilder.applyStructuralRules();
            context.schema(schemaBuilder.build());
        } catch (ConnectorException e) {
            // ICF type was already set at the boundary (network failure, bad credentials,
            // SCIM discovery) — never re-wrap or relabel it.
            throw e;
        } catch (Exception e) {
            // A broken Groovy schema script, a missing script resource or an inconsistent
            // schema definition all make the connector unusable by configuration.
            throw new ConfigurationException(
                    "Failed to initialize the connector configuration: " + HttpExceptionMapper.causeMessage(e), e);
        }
    }

    private void initializeHandlers0() {
        try {
            if (context.isScimEnabled()) {
                context.scim().contributeToHandlers(handlersBuilder);
            }

            initializeObjectClassHandler(handlersBuilder);

            context.handlers(handlersBuilder.build());
        } catch (ConnectorException e) {
            // ICF type was already set at the boundary — never re-wrap or relabel it.
            throw e;
        } catch (Exception e) {
            // A broken Groovy operation script or an inconsistent handler definition makes
            // the connector unusable by configuration.
            throw new ConfigurationException(
                    "Failed to build the connector operation handlers: " + HttpExceptionMapper.causeMessage(e), e);
        }
    }

    /**
     * Classifies the outcome of a test-endpoint probe: 401/403 are bad credentials, 404 is a
     * misconfigured test endpoint, anything else is a (transient) connection problem.
     */
    private static RuntimeException testEndpointStatusException(int status, String detail, String testUrl, Throwable cause) {
        String tail = (detail == null || detail.isBlank()) ? "" : ": " + detail;
        String base = "HTTP " + status + " at " + testUrl + tail;
        return switch (status) {
            case 401, 403 -> new InvalidCredentialException("Authentication required: " + base, cause);
            case 404 -> new ConfigurationException("Test endpoint returned 404 (check the configured test endpoint): " + base, cause);
            default -> new ConnectionFailedException("Connection failed: " + base, cause);
        };
    }

    private boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 400;
    }

    // --------------------------------------------------------------------------
    // Top-level boundary safety net
    // --------------------------------------------------------------------------
    //
    // The SCIM operation handlers translate {@link ScimHttpErrorException} (a custom
    // transport carrier) into the concrete ICF type for their operation. But the ConnId
    // base rethrows any {@code ConnectorException} verbatim, so a handler that forgets the
    // translation would leak the custom type to the ConnId layer, where it is not a
    // recognized built-in exception. These overrides guarantee the translation happens at
    // the boundary no matter what, using a kind-neutral default (the per-operation,
    // kind-specific translation is still done by the handlers themselves).

    private <T> T withStandardIcfBoundary(Callable<T> operation) {
        try {
            return operation.call();
        } catch (ScimHttpErrorException e) {
            // Last-resort translation so the custom carrier never reaches the ConnId layer.
            throw ScimExceptionMapper.map(e, HttpStatusMapper.OperationKind.GET, null);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new ConnectorException(e);
        }
    }

    /**
     * YAML counterpart of the schema branch above — {@code compile} only runs the static syntax
     * check, {@code build} loads the candidate for real via {@link YamlSchemaLoader} onto a
     * throwaway builder seeded with the deployed siblings.
     */
    private ScriptValidationResult validateYamlSchema(ScriptValidationRequest request) {
        var builder = new RestSchemaBuilderImpl(getClass(), context);
        var loader = new SchemaDefinitionLoader(context.configuration().groovyContext(), builder);
        schemaResources(request.filename()).forEach(loader::loadFromResource);

        RestSchema[] candidateSchema = new RestSchema[1];
        var result = YamlScriptValidator.validate(
                request,
                document -> GroovySyntaxChecker.checkObjectClasses(document, RestObjectClassSchemaBuilder.class,
                        new GroovyScriptCompiler(context.configuration().groovyContext())),
                () -> new YamlSchemaLoader(builder).load(request.scriptText()),
                () -> {
                    builder.applyStructuralRules();
                    candidateSchema[0] = builder.build();
                });
        if (result.status() != ScriptValidationResult.Status.OK
                || !ScriptValidationRequest.SCRIPT_OPERATION_BUILD.equals(request.operation())) {
            return result;
        }
        return validateOperationsAgainstCandidateSchema(candidateSchema[0]);
    }

    /**
     * YAML counterpart of the operations branch above — same split as {@link #validateYamlSchema}.
     * Sibling resources are dispatched per file extension ({@link #loadOperationSibling}), since
     * {@link GroovyRestHandlerBuilder#loadFromResource} only reads Groovy.
     */
    private ScriptValidationResult validateYamlOperations(ScriptValidationRequest request) {
        initializeCore();
        var builder = new GroovyRestHandlerBuilder(context.configuration().groovyContext(), context);
        for (String resource : operationResources(request.filename())) {
            loadOperationSibling(builder, resource);
        }
        return YamlScriptValidator.validate(
                request,
                document -> GroovySyntaxChecker.checkOperations(document, BaseOperationSupportBuilder.class,
                        AuthenticationCustomizationBuilder.class, new GroovyScriptCompiler(context.configuration().groovyContext())),
                () -> new YamlRestHandlerLoader(builder, context.configuration().groovyContext()).loadFromString(request.scriptText()),
                builder::build);
    }

    private void loadOperationSibling(RestHandlerBuilder builder, String resource) {
        if (ScriptResources.isYaml(resource)) {
            new YamlRestHandlerLoader(builder, context.configuration().groovyContext())
                    .load(new InputStreamReader(getClass().getResourceAsStream(resource)), resource);
        } else {
            ((GroovyRestHandlerBuilder) builder).loadFromResource(resource);
        }
    }

    private ScriptValidationResult validateOperationsAgainstCandidateSchema(RestSchema candidateSchema) {
        var candidateContext = new RestConnectorContext(context.configuration());
        candidateContext.schema(candidateSchema);
        var checks = operationResources(null).stream()
                .<Callable<ScriptValidationResult>>map(resource -> () -> {
                    var handlerBuilder = new GroovyRestHandlerBuilder(context.configuration().groovyContext(), candidateContext);
                    return GroovyScriptValidator.validateResource(() -> loadOperationSibling(handlerBuilder, resource), handlerBuilder::build);
                })
                .toList();
        return GroovyScriptValidator.combine(checks);
    }
}
