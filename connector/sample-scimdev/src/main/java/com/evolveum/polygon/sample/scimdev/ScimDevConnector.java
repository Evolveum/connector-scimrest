/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sample.scimdev;

import com.evolveum.polygon.scimrest.groovy.connector.AbstractGroovyRestConnector;
import com.evolveum.polygon.scimrest.groovy.handler.GroovyRestHandlerBuilder;
import com.evolveum.polygon.conndev.groovy.GroovySchemaLoader;
import org.identityconnectors.framework.spi.ConnectorClass;

@ConnectorClass(displayNameKey = "scimdev.rest.display", configurationClass = ScimDevConfiguration.class, messageCatalogPaths = "Messages")
public class ScimDevConnector extends AbstractGroovyRestConnector<ScimDevConfiguration> {

    @Override
    protected void initializeSchema(GroovySchemaLoader loader) {
        loader.loadFromResource("/ScimDev.schema.groovy");

    }

    @Override
    protected void initializeAuthorizationHandler(GroovyRestHandlerBuilder builder) {}

    @Override
    protected void initializeObjectClassHandler(GroovyRestHandlerBuilder builder) {

    }

}
