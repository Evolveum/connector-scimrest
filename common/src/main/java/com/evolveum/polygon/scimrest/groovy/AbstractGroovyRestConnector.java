/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy;
import com.evolveum.polygon.scimrest.ClassHandlerConnectorBase;
import com.evolveum.polygon.scimrest.ContextLookup;
import com.evolveum.polygon.scimrest.ObjectClassHandler;
import com.evolveum.polygon.scimrest.api.AuthorizationCustomizer;
import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.config.ScimClientConfiguration;
import com.evolveum.polygon.scimrest.schema.RestSchemaBuilder;
import jakarta.ws.rs.WebApplicationException;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.codehaus.groovy.syntax.SyntaxException;
import org.identityconnectors.framework.common.exceptions.ConnectionBrokenException;
import org.identityconnectors.framework.common.exceptions.ConnectionFailedException;
import org.identityconnectors.framework.common.exceptions.InvalidCredentialException;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.ScriptContext;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.operations.ScriptOnResourceOp;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractGroovyRestConnector<T extends BaseGroovyConnectorConfiguration> extends ClassHandlerConnectorBase implements ScriptOnResourceOp {

    public static final String SCRIPT_ARGUMENT_OPERATION = "operation";
    public static final String SCRIPT_OPERATION_VALIDATE = "validate";
    public static final String SCRIPT_ARGUMENT_ARTIFACT_KIND = "artifactKind";
    public static final String ARTIFACT_KIND_SCHEMA = "schema";

    private final boolean reinitializeOnEachCall;

    private boolean coreInitialized;
    private boolean handlersInitialized;
    private ConnectorContext context;
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
            context = new ConnectorContext(groovyConf);
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
        var schemaBuilder = new RestSchemaBuilder(getClass(), context);
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

    @Override
    public Object runScriptOnResource(ScriptContext request, OperationOptions options) {
        if (!Boolean.TRUE.equals(getConfiguration().getDevelopmentMode())) {
            throw new UnsupportedOperationException("Script execution is supported only in development mode");
        }
        if (!"groovy".equalsIgnoreCase(request.getScriptLanguage())) {
            throw new IllegalArgumentException("Unsupported script language: " + request.getScriptLanguage());
        }
        if (!SCRIPT_OPERATION_VALIDATE.equals(request.getScriptArguments().get(SCRIPT_ARGUMENT_OPERATION))) {
            throw new UnsupportedOperationException(
                    "Unsupported script operation, only '" + SCRIPT_OPERATION_VALIDATE + "' is supported");
        }
        try {
            initializeCore();
        } catch (Exception e) {
            return validationError("initialization", e);
        }
        return validateScript(request.getScriptText(),
                (String) request.getScriptArguments().get(SCRIPT_ARGUMENT_ARTIFACT_KIND));
    }

    /**
     * Validates the script without touching the deployed scripts or the target system.
     * Operation and authorization scripts are compiled, evaluated against a throwaway builder
     * and built. Schema mapping scripts are compiled and evaluated, but the build phase is
     * skipped (see {@link #validateSchemaScript(String)}).
     *
     * @return map with {@code status} ({@code ok}/{@code error}) and for errors also
     *         {@code phase} ({@code compile}/{@code evaluate}/{@code build}), {@code message}
     *         and where available also the {@code line}, {@code column} and {@code source}.
     */
    private Map<String, Object> validateScript(String scriptText, String artifactKind) {
        if (ARTIFACT_KIND_SCHEMA.equals(artifactKind)) {
            return validateSchemaScript(scriptText);
        }

        var builder = new GroovyRestHandlerBuilder(context.configuration().groovyContext(), context);
        groovy.lang.Script script;
        try {
            script = builder.parse(scriptText);
        } catch (CompilationFailedException e) {
            return validationError("compile", e);
        }
        try {
            script.run();
        } catch (Exception e) {
            return validationError("evaluate", e);
        }
        try {
            builder.build();
        } catch (Exception e) {
            return validationError("build", e);
        }
        return Map.of("status", "ok");
    }

    /**
     * Validates the schema mapping script by compiling and evaluating it against a throwaway
     * schema builder. The build phase is skipped: the complete schema is assembled from all
     * schema scripts together, so building just the validated one would fail on definitions
     * declared by its siblings.
     */
    private Map<String, Object> validateSchemaScript(String scriptText) {
        var loader = new SchemaDefinitionLoader(
                context.configuration().groovyContext(), new RestSchemaBuilder(getClass(), context));
        groovy.lang.Script script;
        try {
            script = loader.parse(scriptText);
        } catch (CompilationFailedException e) {
            return validationError("compile", e);
        }
        try {
            script.run();
        } catch (Exception e) {
            return validationError("evaluate", e);
        }
        return Map.of("status", "ok");
    }

    private static Map<String, Object> validationError(String phase, Exception e) {
        var ret = new HashMap<String, Object>();
        ret.put("status", "error");
        ret.put("phase", phase);
        String message = e.getMessage() != null ? e.getMessage() : e.toString();
        var syntaxError = firstSyntaxError(e);
        if (syntaxError != null) {
            ret.put("line", syntaxError.getLine());
            ret.put("column", syntaxError.getStartColumn());
        } else {
            var frame = scriptFrame(e);
            if (frame != null) {
                ret.put("line", frame.getLineNumber());
                if (!frame.getFileName().matches("Script\\d+\\.groovy")) {
                    ret.put("source", frame.getFileName());
                    message = frame.getFileName() + ": " + message;
                }
            }
        }
        ret.put("message", message);
        return ret;
    }

    private static SyntaxException firstSyntaxError(Exception e) {
        if (e instanceof MultipleCompilationErrorsException compilationErrors
                && compilationErrors.getErrorCollector().getErrorCount() > 0
                && compilationErrors.getErrorCollector().getError(0) instanceof SyntaxErrorMessage syntaxError) {
            return syntaxError.getCause();
        }
        return null;
    }

    private static StackTraceElement scriptFrame(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            for (StackTraceElement element : cause.getStackTrace()) {
                if (element.getFileName() != null && element.getFileName().endsWith(".groovy")) {
                    return element;
                }
            }
        }
        return null;
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
