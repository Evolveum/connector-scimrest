/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy;
import com.evolveum.polygon.conndev.spi.ClassHandlerConnectorBase;
import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.groovy.BaseGroovyConnectorConfiguration;
import com.evolveum.polygon.conndev.groovy.GroovyScriptValidator;
import com.evolveum.polygon.conndev.groovy.GroovySchemaLoader;
import com.evolveum.polygon.conndev.groovy.ScriptValidationRequest;
import com.evolveum.polygon.conndev.groovy.ScriptValidationResult;
import com.evolveum.polygon.conndev.spi.ObjectClassHandler;
import com.evolveum.polygon.scimrest.api.AuthorizationCustomizer;
import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.config.ScimClientConfiguration;
import com.evolveum.polygon.scimrest.schema.RestSchema;
import com.evolveum.polygon.scimrest.schema.RestSchemaBuilderImpl;
import jakarta.ws.rs.WebApplicationException;
import org.identityconnectors.framework.common.exceptions.ConnectionBrokenException;
import org.identityconnectors.framework.common.exceptions.ConnectionFailedException;
import org.identityconnectors.framework.common.exceptions.InvalidCredentialException;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.spi.Configuration;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.concurrent.Callable;

public abstract class AbstractGroovyRestConnector<T extends BaseGroovyConnectorConfiguration> extends ClassHandlerConnectorBase {

    private final boolean reinitializeOnEachCall;

    private boolean coreInitialized;
    private boolean handlersInitialized;
    private RestConnectorContext context;
    private GroovyRestHandlerBuilder handlersBuilder;

    @Deprecated
    protected AbstractGroovyRestConnector() {
        this(true);
    }

    protected AbstractGroovyRestConnector(boolean reinitializeOnEachCall) {
        this.reinitializeOnEachCall = reinitializeOnEachCall;
    }


    @Override
    public BaseGroovyConnectorConfiguration getConfiguration() {
        return context.configuration();
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
            throw new IllegalArgumentException("Configuration must be an instance of AbstractGroovyConnectorConfiguration");
        }
    }

    private void initializeCore() {
        if (reinitializeOnEachCall || !coreInitialized) {
            initializeCore0();
            coreInitialized = true;
            handlersInitialized = false;
        }
    }

    private void initializeHandlers() {
        if (reinitializeOnEachCall || !handlersInitialized) {
            initializeHandlers0();
            handlersInitialized = true;
        }
    }

    private void initializeCore0() {
        var schemaBuilder = new RestSchemaBuilderImpl(getClass(), context);
        var schemaLoader = new SchemaDefinitionLoader(context.configuration().groovyContext(), schemaBuilder);
        initializeSchema(schemaLoader);
        context.baseSchema(schemaLoader.baseSchema());

        handlersBuilder = context.handlerBuilder(context.configuration().groovyContext());
        initializeAuthorizationHandler(handlersBuilder);

        context.initializeRest(handlersBuilder.restCustomizer());
        context.initializeScim(handlersBuilder.scimCustomizer());
        if (context.isScimEnabled()) {
            context.scim().initialize();
            context.scim().contributeToSchema(schemaBuilder);
        }

        context.schema(schemaBuilder.build());
    }

    private void initializeHandlers0() {
        if (context.isScimEnabled()) {
            context.scim().contributeToHandlers(handlersBuilder);
        }

        initializeObjectClassHandler(handlersBuilder);

        context.handlers(handlersBuilder.build());
    }

    protected abstract void initializeAuthorizationHandler(GroovyRestHandlerBuilder builder);

    protected AuthorizationCustomizer<RestClientConfiguration> authorizationCustomizer() {
        return (c,v) -> {};
    }

    /**
     * Creates initial configuration for Abstract Groovy Connector
     *
     * @param loader
     */
    protected abstract void initializeSchema(GroovySchemaLoader loader);


    protected abstract void initializeObjectClassHandler(GroovyRestHandlerBuilder builder);


    @Override
    public void test() {
        initializeCore();
        // SCIM Test connection is done automatically during schema discovery
        // FIXME: But makes sense to do again, if connector is poolable (in future)
        var restClientConfig = getConfiguration().configuration(RestClientConfiguration.class);
        if (restClientConfig != null && restClientConfig.getRestTestEndpoint() != null) {
            if (context.rest() != null && context.rest().isPreferenceActive()) {
                context.rest().runProbe();
            } else if (context.isScimEnabled()) {
                var scimBase = ((ScimClientConfiguration) getConfiguration()).getScimBaseUrl();
                var testUrl = scimBase + restClientConfig.getRestTestEndpoint();

                try {
                    context.scim().httpClient().target(testUrl).request().get().close();
                } catch (WebApplicationException e) {
                    var status = e.getResponse().getStatus();
                    switch (status) {
                        case 401:
                        case 403:
                            throw new InvalidCredentialException("Authentication required, HTTP status code " + status, e);
                        default:
                            throw new ConnectionFailedException("Connection failed. HTTP status code " + status, e);
                    }
                } catch (Exception e) {
                    throw new ConnectionFailedException(e.getMessage(), e);
                }
            } else if (context.rest() != null) {
                var request = context.rest().newRequest();
                request.subpath(restClientConfig.getRestTestEndpoint());
                try {
                    var response = context.rest().executeRequest(request, HttpResponse.BodyHandlers.discarding());
                    if (!isSuccess(response.statusCode())) {
                        switch (response.statusCode()) {
                            case 401:
                            case 403:
                                throw new InvalidCredentialException("Authentication required, HTTP status code " + response.statusCode());
                            default:
                                throw new ConnectionFailedException("Connection failed. HTTP status code " + response.statusCode());
                        }
                    }
                } catch (IOException e) {
                    throw new ConnectionFailedException(e);
                } catch (IllegalArgumentException e) {
                    throw new ConnectionFailedException("DNS or URI configuration error: " + e.getMessage(), e);
                } catch (InterruptedException e) {
                    throw new ConnectionBrokenException("Operation was interrupted", e);
                }
            }
        }
    }

    private boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 400;
    }

    @Override
    public Schema schema() {
        initializeCore();
        return context.schema().connIdSchema();
    }

    /**
     * Validates the candidate script against a throwaway target seeded with all currently
     * deployed sibling scripts (via {@link #schemaResources} / {@link #operationResources}, minus
     * {@code filename} itself), so cross-references to them (e.g. a schema attribute's {@code
     * referencedObjectClass}) resolve during evaluation and build, and so the candidate replaces
     * rather than merges with its own old content. For a schema candidate that builds
     * successfully, also re-checks every deployed operation script against the candidate schema
     * (not the currently deployed one), since a schema change can break an operation script that
     * references the changed definitions.
     */
    @Override
    protected ScriptValidationResult validateScript(ScriptValidationRequest request) throws Exception {
        if (ScriptValidationRequest.ARTIFACT_KIND_SCHEMA.equals(request.artifactKind())) {
            var builder = new RestSchemaBuilderImpl(getClass(), context);
            var loader = new SchemaDefinitionLoader(context.configuration().groovyContext(), builder);
            schemaResources(request.filename()).forEach(loader::loadFromResource);
            RestSchema[] candidateSchema = new RestSchema[1];
            var schemaResult = GroovyScriptValidator.validate(
                    loader::parse, () -> candidateSchema[0] = builder.build(), request.scriptText(), request.operation());
            if (schemaResult.status() != ScriptValidationResult.Status.OK
                    || !ScriptValidationRequest.SCRIPT_OPERATION_BUILD.equals(request.operation())) {
                return schemaResult;
            }
            return validateOperationsAgainstCandidateSchema(candidateSchema[0]);
        }
        initializeCore();
        var builder = new GroovyRestHandlerBuilder(context.configuration().groovyContext(), context);
        operationResources(request.filename()).forEach(builder::loadFromResource);
        return GroovyScriptValidator.validate(builder::parse, builder::build, request.scriptText(), request.operation());
    }

    private ScriptValidationResult validateOperationsAgainstCandidateSchema(RestSchema candidateSchema) {
        var candidateContext = new RestConnectorContext(context.configuration());
        candidateContext.schema(candidateSchema);
        var checks = operationResources(null).stream()
                .<Callable<ScriptValidationResult>>map(resource -> () -> {
                    var handlerBuilder = new GroovyRestHandlerBuilder(context.configuration().groovyContext(), candidateContext);
                    return GroovyScriptValidator.validateResource(() -> handlerBuilder.loadFromResource(resource), handlerBuilder::build);
                })
                .toList();
        return GroovyScriptValidator.combine(checks);
    }

    @Override
    public void dispose() {
        // Dispose of connector
    }

    @Override
    public ContextLookup context() {
        return context;
    }
}
