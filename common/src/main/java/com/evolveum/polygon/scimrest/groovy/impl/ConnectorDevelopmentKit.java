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

public class ConnectorDevelopmentKit extends AbstractGroovyRestConnector<DevelopmentKitConfiguration> {


    @Override
    protected void initializeSchema(GroovySchemaLoader loader) {
        for (var script : getConfiguration().configuration(DevelopmentKitConfiguration.class).getSchemaScripts()) {
            loader.load(script);
        };
    }

    @Override
    protected void initializeObjectClassHandler(GroovyRestHandlerBuilder builder) {
        for(var script : getConfiguration().configuration(DevelopmentKitConfiguration.class).getOperationScripts()) {
            builder.loadFromString(script);
        }
    }
}
