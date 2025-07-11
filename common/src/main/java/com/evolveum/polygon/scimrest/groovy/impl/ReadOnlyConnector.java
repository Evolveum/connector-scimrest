/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */

package com.evolveum.polygon.scimrest.groovy.impl;

import com.evolveum.polygon.scimrest.groovy.AbstractGroovyRestConnector;
import com.evolveum.polygon.scimrest.groovy.GroovyRestHandlerBuilder;
import com.evolveum.polygon.scimrest.groovy.GroovySchemaLoader;
import org.identityconnectors.framework.spi.ConnectorClass;

@ConnectorClass(displayNameKey = "readonly.connector.display", configurationClass = ReadOnlyConfiguration.class)
public class ReadOnlyConnector extends AbstractGroovyRestConnector<ReadOnlyConfiguration> {

    @Override
    protected void initializeObjectClassHandler(GroovyRestHandlerBuilder builder) {
        // FIXME: pointer to static scripted connector entry point script / configuration / manifest
    }

    @Override
    protected void initializeSchema(GroovySchemaLoader loader) {
        loader.loadFromResource("/Schema.schema.groovy");
    }
}
