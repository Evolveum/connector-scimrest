/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.auth.crud;

import com.evolveum.polygon.scimrest.ClassHandlerConnectorBase;
import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import org.identityconnectors.common.security.GuardedString;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;

public class BearerTokenCrudTest extends AbstractAuthOnCrudTest {

    private static final String TOKEN = "crud-bearer-token";

    @Override
    protected ClassHandlerConnectorBase createConnector() {
        var connector = new TestConnector();
        connector.init(new Config(wireMockServer.port()));
        return connector;
    }

    @Override protected void stubAuthPrerequisites() { }

    @Override
    protected RequestPatternBuilder withExpectedAuth(RequestPatternBuilder pattern) {
        return pattern.withHeader("Authorization", equalTo("Bearer " + TOKEN));
    }

    private static class Config extends BaseTestConfiguration
            implements RestClientConfiguration.BearerTokenAuthorization {

        Config(int port) { super(port); }

        @Override public String getRestTestEndpoint() { return null; }
        @Override public GuardedString getRestTokenValue() { return new GuardedString(TOKEN.toCharArray()); }
    }
}
