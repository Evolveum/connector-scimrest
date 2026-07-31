/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.support;

import com.evolveum.polygon.scimrest.groovy.AbstractGroovyRestConnector;
import com.evolveum.polygon.scimrest.groovy.BaseRestGroovyConnectorConfiguration;
import com.evolveum.polygon.scimrest.groovy.GroovyRestHandlerBuilder;
import com.evolveum.polygon.scimrest.groovy.GroovySchemaLoader;

public class TestRestConnector extends AbstractGroovyRestConnector<BaseRestGroovyConnectorConfiguration> {
    private final BaseRestGroovyConnectorConfiguration configuration;

    public TestRestConnector(BaseRestGroovyConnectorConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected void initializeSchema(GroovySchemaLoader loader) {
        // Empty schema for testing
    }

    @Override
    protected void initializeAuthorizationHandler(GroovyRestHandlerBuilder builder) {}

    @Override
    protected void initializeObjectClassHandler(GroovyRestHandlerBuilder builder) {
        // No handlers needed for test
    }
}
