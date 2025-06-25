/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scim.rest.groovy;
import com.evolveum.polygon.scim.rest.ClassHandlerConnectorBase;
import com.evolveum.polygon.scim.rest.ContextLookup;
import com.evolveum.polygon.scim.rest.ObjectClassHandler;
import com.evolveum.polygon.scim.rest.RestContext;
import com.evolveum.polygon.scim.rest.config.RestClientConfiguration;
import com.evolveum.polygon.scim.rest.schema.RestSchemaBuilder;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.spi.Configuration;

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

        var schemaBuilder = new RestSchemaBuilder(getClass());

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
        context.initializeRest(authorizationCustomizer());
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


        // FIXME: Implement later
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
