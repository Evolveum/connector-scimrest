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
import static com.github.tomakehurst.wiremock.client.WireMock.matching;

public class GroovyAwsSignatureCrudTest extends AbstractGroovyAuthCrudTest {

    private static final String GROOVY_HEADER = "X-Groovy-Aws";
    private static final String GROOVY_HEADER_VALUE = "groovy-hook";

    @Override
    protected String authScript() {
        return """
                authentication {
                    rest {
                        awsSignature {
                            beforeSign {
                                request.header("%s", "%s")
                            }
                        }
                    }
                }
                """.formatted(GROOVY_HEADER, GROOVY_HEADER_VALUE);
    }

    @Override
    protected BaseTestConfiguration createConfig(int port) {
        return new Config(port);
    }

    @Override
    protected void stubAuthPrerequisites() { }

    @Override
    protected RequestPatternBuilder withExpectedAuth(RequestPatternBuilder pattern) {
        return pattern
                .withHeader("Authorization", matching("AWS4-HMAC-SHA256.*"))
                .withHeader(GROOVY_HEADER, equalTo(GROOVY_HEADER_VALUE));
    }

    private static class Config extends BaseTestConfiguration
            implements RestClientConfiguration.AwsSignatureAuthorization {

        Config(int port) { super(port); }

        @Override public String getRestTestEndpoint() { return null; }
        @Override public String getRestAwsAccessKey() { return "AKIATEST"; }
        @Override public GuardedString getRestAwsSecretKey() { return new GuardedString("test-secret-key".toCharArray()); }
        @Override public String getRestAwsRegion() { return "us-east-1"; }
        @Override public String getRestAwsService() { return "execute-api"; }
        @Override public GuardedString getRestAwsSessionToken() { return null; }
    }
}
