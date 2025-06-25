/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sample.scimdev;

import com.evolveum.polygon.scimrest.groovy.AbstractGroovyRestConnector;
import com.evolveum.polygon.scimrest.groovy.GroovyRestHandlerBuilder;
import com.evolveum.polygon.scimrest.groovy.GroovySchemaLoader;
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
