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

public class GroovyJwtBearerCrudTest extends AbstractGroovyAuthCrudTest {

    private static final String CUSTOM_SCHEME = "CustomJWT";
    private static final String PAYLOAD_VALUE = "groovy-jwt-payload";

    @Override
    protected String authScript() {
        return """
                authentication {
                    rest {
                        jwtBearer {
                            implementation {
                                request.header("Authorization", "%s " + configuration.getRestJwtPayload())
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
        return pattern.withHeader("Authorization", equalTo(CUSTOM_SCHEME + " " + PAYLOAD_VALUE));
    }

    private static class Config extends BaseTestConfiguration
            implements RestClientConfiguration.JwtBearerAuthorization {

        Config(int port) { super(port); }

        @Override public String getRestTestEndpoint() { return null; }
        @Override public String getRestJwtTokenName() { return "Bearer"; }
        @Override public String getRestJwtAlgorithm() { return null; }
        @Override public GuardedString getRestJwtSecret() { return null; }
        @Override public Boolean getRestJwtSecretBase64Encoded() { return false; }
        @Override public String getRestJwtPayload() { return PAYLOAD_VALUE; }
        @Override public String getRestJwtLocation() { return "header"; }
    }
}
