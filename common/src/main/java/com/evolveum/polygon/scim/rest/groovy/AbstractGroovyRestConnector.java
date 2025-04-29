package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.AbstractRestJsonObjectClassHandler;
import com.evolveum.polygon.scim.rest.ClassHandlerConnectorBase;
import groovy.lang.GroovyClassLoader;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.spi.Configuration;

import java.util.Map;

public abstract class AbstractGroovyRestConnector extends ClassHandlerConnectorBase {

    Map<ObjectClass, AbstractRestJsonObjectClassHandler> handlers;

    @Override
    public void init(Configuration cfg) {
        var loader = new GroovySchemaLoader();
        initializeSchema();



    }

    /**
     * Creates initial configuration for Abstract Groovy Connector
     *
     * @param builder
     */
    protected abstract void initializeSchema(GroovySchemaLoader loader);


    protected abstract void initializeObjectClassHandler(HandlersBuilder builder);

}
