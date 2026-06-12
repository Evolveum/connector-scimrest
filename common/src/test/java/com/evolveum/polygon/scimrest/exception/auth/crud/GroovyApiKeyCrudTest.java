/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.exception.auth.crud;

import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import org.identityconnectors.common.security.GuardedString;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;

public class GroovyApiKeyCrudTest extends AbstractGroovyAuthCrudTest {

    private static final String API_KEY = "groovy-crud-api-key";
    private static final String CUSTOM_HEADER = "X-Groovy-Api-Key";

    @Override
    protected String authScript() {
        return """
                authentication {
                    rest {
                        apiKey {
                            implementation {
                                request.header("%s", decrypt(configuration.getRestApiKey()))
                            }
                        }
                    }
                }
                """.formatted(CUSTOM_HEADER);
    }

    @Override
    protected BaseTestConfiguration createConfig(int port) {
        return new Config(port);
    }

    @Override
    protected void stubAuthPrerequisites() { }

    @Override
    protected RequestPatternBuilder withExpectedAuth(RequestPatternBuilder pattern) {
        return pattern.withHeader(CUSTOM_HEADER, equalTo(API_KEY));
    }

    private static class Config extends BaseTestConfiguration
            implements RestClientConfiguration.ApiKeyAuthorization {

        Config(int port) { super(port); }

        @Override public String getRestTestEndpoint() { return null; }
        @Override public GuardedString getRestApiKey() { return new GuardedString(API_KEY.toCharArray()); }
        @Override public String getRestApiKeyName() { return "X-Api-Key"; }
        @Override public String getRestApiKeyLocation() { return "header"; }
    }
}
