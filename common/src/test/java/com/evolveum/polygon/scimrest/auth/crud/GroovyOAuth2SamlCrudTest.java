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
import org.testng.annotations.BeforeMethod;

import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.Base64;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

public class GroovyOAuth2SamlCrudTest extends AbstractGroovyAuthCrudTest {

    private static final String TOKEN_ENDPOINT = "/oauth2/token";
    private static final String ACCESS_TOKEN = "groovy-saml-crud-token";
    private static final String CUSTOM_HEADER = "X-Groovy-Token";

    private GuardedString privateKeyPem;

    @BeforeMethod
    @Override
    public void setUp() throws Exception {
        super.setUp();
        var gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        privateKeyPem = new GuardedString(toPem(gen.generateKeyPair().getPrivate()).toCharArray());
    }

    @Override
    protected String authScript() {
        return """
                authentication {
                    rest {
                        oauth2Saml { oauth2Context ->
                            applyToken { request ->
                                request.header("%s", oauth2Context["access_token"])
                            }
                        }
                    }
                }
                """.formatted(CUSTOM_HEADER);
    }

    @Override
    protected BaseTestConfiguration createConfig(int port) {
        return new Config(port,
                "http://localhost:" + port + TOKEN_ENDPOINT,
                "groovy-client", "groovy-issuer", privateKeyPem);
    }

    @Override
    protected void stubAuthPrerequisites() {
        wireMockServer.stubFor(post(urlEqualTo(TOKEN_ENDPOINT))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"" + ACCESS_TOKEN + "\",\"token_type\":\"Bearer\",\"expires_in\":3600}")));
    }

    @Override
    protected RequestPatternBuilder withExpectedAuth(RequestPatternBuilder pattern) {
        return pattern.withHeader(CUSTOM_HEADER, equalTo(ACCESS_TOKEN));
    }

    private static String toPem(PrivateKey key) {
        String encoded = Base64.getEncoder().encodeToString(key.getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + encoded + "\n-----END PRIVATE KEY-----\n";
    }

    private static class Config extends BaseTestConfiguration
            implements RestClientConfiguration.OAuth2SamlAuthorization {

        private final String tokenUrl;
        private final String clientId;
        private final String issuer;
        private final GuardedString privateKey;

        Config(int port, String tokenUrl, String clientId, String issuer, GuardedString privateKey) {
            super(port);
            this.tokenUrl = tokenUrl;
            this.clientId = clientId;
            this.issuer = issuer;
            this.privateKey = privateKey;
        }

        @Override public String getRestOAuth2TokenUrl() { return tokenUrl; }
        @Override public String getRestOAuth2ClientId() { return clientId; }
        @Override public GuardedString getRestOAuth2PrivateKey() { return privateKey; }
        @Override public String getRestOAuth2Issuer() { return issuer; }
        @Override public String getRestOAuth2Scope() { return null; }
        @Override public String getRestOAuth2ClientAuthenticationScheme() { return null; }
    }
}
