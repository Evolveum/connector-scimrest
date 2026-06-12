/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.exception.auth.crud;

import com.evolveum.polygon.scimrest.ClassHandlerConnectorBase;
import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import org.identityconnectors.common.security.GuardedString;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;

public class ApiKeyCrudTest extends AbstractAuthOnCrudTest {

    private static final String API_KEY = "crud-test-api-key";
    private static final String HEADER_NAME = "X-Api-Key";

    @Override
    protected ClassHandlerConnectorBase createConnector() {
        var connector = new TestConnector();
        connector.init(new Config(wireMockServer.port()));
        return connector;
    }

    @Override protected void stubAuthPrerequisites() { }

    @Override
    protected RequestPatternBuilder withExpectedAuth(RequestPatternBuilder pattern) {
        return pattern.withHeader(HEADER_NAME, equalTo(API_KEY));
    }

    private static class Config extends BaseTestConfiguration
            implements RestClientConfiguration.ApiKeyAuthorization {

        Config(int port) { super(port); }

        @Override public String getRestTestEndpoint() { return null; }
        @Override public GuardedString getRestApiKey() { return new GuardedString(API_KEY.toCharArray()); }
        @Override public String getRestApiKeyName() { return HEADER_NAME; }
        @Override public String getRestApiKeyLocation() { return "header"; }
    }
}
