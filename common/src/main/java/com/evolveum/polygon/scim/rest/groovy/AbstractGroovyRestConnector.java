package com.evolveum.polygon.scim.rest.groovy;
import com.evolveum.polygon.scim.rest.ClassHandlerConnectorBase;
import com.evolveum.polygon.scim.rest.ObjectClassHandler;
import com.evolveum.polygon.scim.rest.RestContext;
import com.evolveum.polygon.scim.rest.config.HttpClientConfiguration;
import com.evolveum.polygon.scim.rest.schema.RestSchemaBuilder;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.spi.Configuration;

public abstract class AbstractGroovyRestConnector<T extends BaseGroovyConnectorConfiguration> extends ClassHandlerConnectorBase<RestContext> {

    private ConnectorContext context;

    @Override
    public BaseGroovyConnectorConfiguration getConfiguration() {
        return context.configuration();
    }


    @Override
    public ObjectClassHandler<RestContext> handlerFor(ObjectClass objectClass) throws UnsupportedOperationException {
        return context.handlerFor(objectClass);
    }

    @Override
    public void init(Configuration cfg) {

        if (cfg instanceof BaseGroovyConnectorConfiguration groovyConf) {
            context = new ConnectorContext(groovyConf);
            var schemaBuilder = new RestSchemaBuilder(getClass());
            initializeSchema(new GroovySchemaLoader(groovyConf.groovyContext(), schemaBuilder));
            context.schema(schemaBuilder.build());
            var handlersBuilder = context.handlerBuilder(groovyConf.groovyContext());
            initializeObjectClassHandler(handlersBuilder);
            context.handlers(handlersBuilder.build());
            initializeRestClient();
            
        } else {
            throw new IllegalArgumentException("Configuration must be an instance of AbstractGroovyConnectorConfiguration");
        }
    }

    private void initializeRestClient() {
        context.rest(new RestContext((HttpClientConfiguration) context.configuration(), authorizationCustomizer()));
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
        return context.schema().connIdSchema();
    }

    @Override
    public void dispose() {
        // Dispose of connector
    }

    @Override
    public RestContext context() {
        return context.rest();
    }
}
