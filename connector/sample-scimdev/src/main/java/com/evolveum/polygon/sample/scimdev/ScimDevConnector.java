package com.evolveum.polygon.sample.scimdev;

import com.evolveum.polygon.common.GuardedStringAccessor;
import com.evolveum.polygon.scim.rest.RestContext;
import com.evolveum.polygon.scim.rest.config.RestClientConfiguration;
import com.evolveum.polygon.scim.rest.groovy.AbstractGroovyRestConnector;
import com.evolveum.polygon.scim.rest.groovy.GroovyRestHandlerBuilder;
import com.evolveum.polygon.scim.rest.groovy.GroovySchemaLoader;
import org.identityconnectors.framework.spi.ConnectorClass;

@ConnectorClass(displayNameKey = "scimdev.rest.display", configurationClass = ScimDevConfiguration.class)
public class ScimDevConnector extends AbstractGroovyRestConnector<ScimDevConfiguration> {

    @Override
    protected void initializeSchema(GroovySchemaLoader loader) {
        loader.loadFromResource("/ScimDev.schema.groovy");

    }

    @Override
    protected void initializeObjectClassHandler(GroovyRestHandlerBuilder builder) {

    }

}
