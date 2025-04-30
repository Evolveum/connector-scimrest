package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.AbstractRestJsonObjectClassHandler;
import com.evolveum.polygon.scim.rest.ClassHandlerConnectorBase;
import com.evolveum.polygon.scim.rest.ObjectClassHandler;
import com.evolveum.polygon.scim.rest.schema.RestSchema;
import com.evolveum.polygon.scim.rest.schema.RestSchemaBuilder;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.spi.Configuration;

import java.util.Map;

public abstract class AbstractGroovyRestConnector<T extends AbstractGroovyConnectorConfiguration> extends ClassHandlerConnectorBase {

    Map<ObjectClass, AbstractRestJsonObjectClassHandler> handlers;
    private AbstractGroovyConnectorConfiguration configuration;
    private RestSchema schema;

    @Override
    public AbstractGroovyConnectorConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public ObjectClassHandler handlerFor(ObjectClass objectClass) throws UnsupportedOperationException {
        return handlers.get(objectClass);
    }



    @Override
    public void init(Configuration cfg) {
        if (cfg instanceof AbstractGroovyConnectorConfiguration groovyConf) {
            this.configuration = groovyConf;
            var schemaBuilder = new RestSchemaBuilder(getClass());
            initializeSchema(new GroovySchemaLoader(groovyConf.groovyContext(), schemaBuilder));
            this.schema = schemaBuilder.build();
            var handlersBuilder = new HandlersBuilder();
            initializeObjectClassHandler(handlersBuilder);
            this.handlers = handlersBuilder.build();

        } else {
            throw new IllegalArgumentException("Configuration must be an instance of AbstractGroovyConnectorConfiguration");
        }
    }

    /**
     * Creates initial configuration for Abstract Groovy Connector
     *
     * @param loader
     */
    protected abstract void initializeSchema(GroovySchemaLoader loader);


    protected abstract void initializeObjectClassHandler(HandlersBuilder builder);


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
}
