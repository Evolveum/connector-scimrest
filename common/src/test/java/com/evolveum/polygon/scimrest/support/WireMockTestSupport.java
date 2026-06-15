/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.support;

import com.evolveum.polygon.scimrest.groovy.BaseRestGroovyConnectorConfiguration;
import com.evolveum.polygon.scimrest.groovy.GroovyRestHandlerBuilder;
import com.evolveum.polygon.scimrest.groovy.GroovySchemaLoader;
import com.evolveum.polygon.scimrest.groovy.impl.ManifestBasedConnector;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

public class WireMockTestSupport {

    protected WireMockServer wireMockServer;

    protected void setUpWireMock() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    protected void tearDownWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
            wireMockServer.resetAll();
        }
    }

    protected static abstract class BaseTestConfiguration extends BaseRestGroovyConnectorConfiguration {
        protected BaseTestConfiguration(int port) {
            setBaseAddress("http://localhost:" + port);
        }
    }

    protected static class ScriptConnector extends ManifestBasedConnector {
        private final String authScript;
        private final String schemaScript;
        private final String operationScript;

        public ScriptConnector(String authScript) {
            this(authScript, null, null);
        }

        public ScriptConnector(String authScript, String schemaScript, String operationScript) {
            this.authScript = authScript;
            this.schemaScript = schemaScript;
            this.operationScript = operationScript;
        }

        @Override
        protected void initializeSchema(GroovySchemaLoader loader) {
            if (schemaScript != null) loader.load(schemaScript);
        }

        @Override
        protected void initializeAuthorizationHandler(GroovyRestHandlerBuilder builder) {
            if (authScript != null) builder.loadFromString(authScript);
        }

        @Override
        protected void initializeObjectClassHandler(GroovyRestHandlerBuilder builder) {
            if (operationScript != null) builder.loadFromString(operationScript);
        }
    }

}
