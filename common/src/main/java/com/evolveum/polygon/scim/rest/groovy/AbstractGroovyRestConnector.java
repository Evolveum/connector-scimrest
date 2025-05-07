package com.evolveum.polygon.scim.rest.groovy;
import com.evolveum.polygon.scim.rest.ClassHandlerConnectorBase;
import com.evolveum.polygon.scim.rest.ObjectClassHandler;
import com.evolveum.polygon.scim.rest.RestContext;
import com.evolveum.polygon.scim.rest.config.HttpClientConfiguration;
import com.evolveum.polygon.scim.rest.schema.RestSchema;
import com.evolveum.polygon.scim.rest.schema.RestSchemaBuilder;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.spi.Configuration;

import java.util.Map;

public abstract class AbstractGroovyRestConnector<T extends BaseGroovyConnectorConfiguration> extends ClassHandlerConnectorBase<RestContext> {

    Map<ObjectClass, ObjectClassHandler<RestContext>> handlers;
    private BaseGroovyConnectorConfiguration configuration;
    private RestSchema schema;
    private RestContext restContext;

    @Override
    public BaseGroovyConnectorConfiguration getConfiguration() {
        return configuration;
    }


    @Override
    public ObjectClassHandler<RestContext> handlerFor(ObjectClass objectClass) throws UnsupportedOperationException {
        return handlers.get(objectClass);
    }

    @Override
    public void init(Configuration cfg) {
        if (cfg instanceof BaseGroovyConnectorConfiguration groovyConf) {
            this.configuration = groovyConf;
            var schemaBuilder = new RestSchemaBuilder(getClass());

            initializeSchema(new GroovySchemaLoader(groovyConf.groovyContext(), schemaBuilder));
            this.schema = schemaBuilder.build();
            var handlersBuilder = new RestHandlerBuilder(this.schema);
            initializeObjectClassHandler(handlersBuilder);
            this.handlers = handlersBuilder.build();
            initializeRestClient();
            
        } else {
            throw new IllegalArgumentException("Configuration must be an instance of AbstractGroovyConnectorConfiguration");
        }
    }

    private void initializeRestClient() {
        restContext = new RestContext((HttpClientConfiguration) configuration, authorizationCustomizer());
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


    protected abstract void initializeObjectClassHandler(RestHandlerBuilder builder);


    @Override
    public void test() {


        // FIXME: Implement later
    }

    @Override
    public Schema schema() {
        return schema.connIdSchema();
    }

    @Override
    public void dispose() {
        // Dispose of connector
    }

    @Override
    public RestContext context() {
        return restContext;
    }
}
