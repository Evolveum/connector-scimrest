/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.exception.auth.crud;

import com.evolveum.polygon.scimrest.ClassHandlerConnectorBase;
import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.AbstractGroovyRestConnector;
import com.evolveum.polygon.scimrest.groovy.BaseRestGroovyConnectorConfiguration;
import com.evolveum.polygon.scimrest.groovy.GroovyRestHandlerBuilder;
import com.evolveum.polygon.scimrest.groovy.GroovySchemaLoader;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import org.identityconnectors.common.security.GuardedString;

import java.util.Base64;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;

public class BasicAuthCrudTest extends AbstractAuthOnCrudTest {

    private static final String USERNAME = "crud-user";
    private static final String PASSWORD = "crud-pass";
    private static final String EXPECTED_HEADER =
            "Basic " + Base64.getEncoder().encodeToString((USERNAME + ":" + PASSWORD).getBytes());

    @Override
    protected ClassHandlerConnectorBase createConnector() {
        var config = new Config(wireMockServer.port());
        var connector = new Connector();
        connector.init(config);
        return connector;
    }

    @Override
    protected void stubAuthPrerequisites() {
    }

    @Override
    protected RequestPatternBuilder withExpectedAuth(RequestPatternBuilder pattern) {
        return pattern.withHeader("Authorization", equalTo(EXPECTED_HEADER));
    }

    private static class Config extends BaseTestConfiguration
            implements RestClientConfiguration.BasicAuthorization {

        Config(int port) { super(port); }

        @Override public String getRestTestEndpoint() { return null; }
        @Override public String getRestUsername() { return USERNAME; }
        @Override public GuardedString getRestPassword() { return new GuardedString(PASSWORD.toCharArray()); }
    }

    private static class Connector extends AbstractGroovyRestConnector<BaseRestGroovyConnectorConfiguration> {
        @Override protected void initializeSchema(GroovySchemaLoader loader) { loader.load(SCHEMA_SCRIPT); }
        @Override protected void initializeAuthorizationHandler(GroovyRestHandlerBuilder builder) { }
        @Override protected void initializeObjectClassHandler(GroovyRestHandlerBuilder builder) { builder.loadFromString(OPERATION_SCRIPT); }
    }
}
