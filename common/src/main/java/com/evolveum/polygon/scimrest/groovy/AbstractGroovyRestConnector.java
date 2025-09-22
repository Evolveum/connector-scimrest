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
import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.impl.rest.RestContext;
import com.evolveum.polygon.scimrest.schema.RestSchemaBuilder;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.exceptions.ConnectionBrokenException;
import org.identityconnectors.framework.common.exceptions.ConnectionFailedException;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.spi.Configuration;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;

public abstract class AbstractGroovyRestConnector<T extends BaseGroovyConnectorConfiguration> extends ClassHandlerConnectorBase {

    private ConnectorContext context;
    @Override
    public BaseGroovyConnectorConfiguration getConfiguration() {
        return context.configuration();
    }

    @Override
    public ObjectClassHandler handlerFor(ObjectClass objectClass) throws UnsupportedOperationException {
        initialize();
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

    private void initialize() {

        var schemaBuilder = new RestSchemaBuilder(getClass(), context);

        initializeSchema(new GroovySchemaLoader(context.configuration().groovyContext(), schemaBuilder));
        // Populate with SCIM schema

        // Before we build final schema, we populate it with schema discovered via SCIM (if SCIM is enabled)
        context.initializeScim();
        if (context.isScimEnabled()) {
            context.scim().initialize();
            context.scim().contributeToSchema(schemaBuilder);
        }

        context.schema(schemaBuilder.build());

        var handlersBuilder = context.handlerBuilder(context.configuration().groovyContext());
        initializeObjectClassHandler(handlersBuilder);


        if (context.isScimEnabled()) {
            context.scim().contributeToHandlers(handlersBuilder);
        }
        context.handlers(handlersBuilder.build());

        // Finally we initialize REST client if present
        var authentication = handlersBuilder.buildAuthentication();

        context.initializeRest(authentication);
    }

    protected RestContext.AuthorizationCustomizer authorizationCustomizer() {
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
        // SCIM Test is done automaticly during schema discovery
        // FIXME: Implement later
        var restClientConfig = getConfiguration().configuration(RestClientConfiguration.class);
        if (restClientConfig != null && restClientConfig.getRestTestEndpoint() != null) {
            var request = context.rest().newAuthorizedRequest();
            request.subpath(restClientConfig.getRestTestEndpoint());
            try {
                var response = context.rest().executeRequest(request, HttpResponse.BodyHandlers.discarding());
                if (!isSuccess(response.statusCode())) {
                    switch (response.statusCode()) {
                        case 401:
                        case 403:
                            throw new ConnectionFailedException("Authentication required, HTTP status code " + response.statusCode());
                        default:
                            throw new ConnectionFailedException("Connection failed. HTTP status code " + response.statusCode());
                    }
                }
            } catch (URISyntaxException e) {
                throw new ConfigurationException("Incorrectly configured address",e);
            } catch (IOException e) {
                throw new ConnectionFailedException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }


    }

    private boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 400;
    }

    @Override
    public Schema schema() {
        initialize();
        return context.schema().connIdSchema();
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
