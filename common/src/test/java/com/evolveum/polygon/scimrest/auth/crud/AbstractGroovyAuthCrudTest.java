/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.auth.crud;

import com.evolveum.polygon.conndev.spi.ClassHandlerConnectorBase;
import com.evolveum.polygon.scimrest.groovy.connector.AbstractGroovyRestConnector;
import com.evolveum.polygon.scimrest.groovy.handler.GroovyRestHandlerBuilder;
import com.evolveum.polygon.conndev.groovy.GroovyScriptLoader;
import com.evolveum.polygon.conndev.groovy.GroovySchemaLoader;

/**
 * Base for CRUD auth tests where authorization is implemented via a Groovy script
 * loaded into initializeAuthorizationHandler, rather than via built-in Java config interfaces.
 */
public abstract class AbstractGroovyAuthCrudTest extends AbstractAuthOnCrudTest {

    protected abstract String authScript();

    protected abstract BaseTestConfiguration createConfig(int port);

    @Override
    protected ClassHandlerConnectorBase createConnector() {
        var connector = new GroovyAuthConnector(authScript());
        connector.init(createConfig(wireMockServer.port()));
        return connector;
    }

    protected static class GroovyAuthConnector
            extends AbstractGroovyRestConnector {

        private final String authScript;

        GroovyAuthConnector(String authScript) {
            this.authScript = authScript;
        }

        @Override
        protected void initializeSchema(GroovySchemaLoader loader) {
            loader.load(NATIVE_SCHEMA_SCRIPT);
            loader.load(CONNID_SCHEMA_SCRIPT);
        }

        @Override
        protected void initializeAuthorizationHandler(GroovyRestHandlerBuilder builder) {
            builder.loadFromString(authScript);
        }

        @Override
        protected void initializeObjectClassHandler(GroovyScriptLoader builder) {
            builder.loadFromString(OPERATION_SCRIPT);
        }
    }
}
