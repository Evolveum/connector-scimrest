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

public class GroovyBasicCrudTest extends AbstractGroovyAuthCrudTest {

    private static final String USERNAME = "groovy-crud-user";
    private static final String PASSWORD = "groovy-crud-pass";
    private static final String CUSTOM_SCHEME = "CustomBasic";

    @Override
    protected String authScript() {
        return """
                authentication {
                    rest {
                        basic {
                            implementation {
                                request.header("Authorization", "%s " + configuration.getRestUsername() + ":" + decrypt(configuration.getRestPassword()))
                            }
                        }
                    }
                }
                """.formatted(CUSTOM_SCHEME);
    }

    @Override
    protected BaseTestConfiguration createConfig(int port) {
        return new Config(port);
    }

    @Override
    protected void stubAuthPrerequisites() { }

    @Override
    protected RequestPatternBuilder withExpectedAuth(RequestPatternBuilder pattern) {
        return pattern.withHeader("Authorization", equalTo(CUSTOM_SCHEME + " " + USERNAME + ":" + PASSWORD));
    }

    private static class Config extends BaseTestConfiguration
            implements RestClientConfiguration.BasicAuthorization {

        Config(int port) { super(port); }

        @Override public String getRestTestEndpoint() { return null; }
        @Override public String getRestUsername() { return USERNAME; }
        @Override public GuardedString getRestPassword() { return new GuardedString(PASSWORD.toCharArray()); }
    }
}
