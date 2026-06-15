/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.auth.crud;

import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import org.identityconnectors.common.security.GuardedString;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;

public class GroovyBearerCrudTest extends AbstractGroovyAuthCrudTest {

    private static final String TOKEN = "groovy-bearer-crud-token";
    private static final String CUSTOM_TOKEN_NAME = "CustomBearer";

    @Override
    protected String authScript() {
        return """
                authentication {
                    rest {
                        bearer {
                            implementation {
                                request.header("Authorization", "%s " + decrypt(configuration.getRestTokenValue()))
                            }
                        }
                    }
                }
                """.formatted(CUSTOM_TOKEN_NAME);
    }

    @Override
    protected BaseTestConfiguration createConfig(int port) {
        return new Config(port);
    }

    @Override
    protected void stubAuthPrerequisites() { }

    @Override
    protected RequestPatternBuilder withExpectedAuth(RequestPatternBuilder pattern) {
        return pattern.withHeader("Authorization", equalTo(CUSTOM_TOKEN_NAME + " " + TOKEN));
    }

    private static class Config extends BaseTestConfiguration
            implements RestClientConfiguration.BearerTokenAuthorization {

        Config(int port) { super(port); }

        @Override public String getRestTestEndpoint() { return null; }
        @Override public GuardedString getRestTokenValue() { return new GuardedString(TOKEN.toCharArray()); }
    }
}
