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

import static com.github.tomakehurst.wiremock.client.WireMock.matching;

public class JwtBearerCrudTest extends AbstractAuthOnCrudTest {

    @Override
    protected ClassHandlerConnectorBase createConnector() {
        var connector = new TestConnector();
        connector.init(new Config(wireMockServer.port()));
        return connector;
    }

    @Override protected void stubAuthPrerequisites() { }

    @Override
    protected RequestPatternBuilder withExpectedAuth(RequestPatternBuilder pattern) {
        return pattern.withHeader("Authorization", matching("Bearer [A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"));
    }

    private static class Config extends BaseTestConfiguration
            implements RestClientConfiguration.JwtBearerAuthorization {

        Config(int port) { super(port); }

        @Override public String getRestTestEndpoint() { return null; }
        @Override public String getRestJwtTokenName() { return "Bearer"; }
        @Override public String getRestJwtAlgorithm() { return "HS256"; }
        @Override public GuardedString getRestJwtSecret() { return new GuardedString("crud-jwt-secret".toCharArray()); }
        @Override public Boolean getRestJwtSecretBase64Encoded() { return false; }
        @Override public String getRestJwtPayload() { return "{}"; }
        @Override public String getRestJwtLocalization() { return "header"; }
    }
}
