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
import org.testng.annotations.BeforeMethod;

import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.Base64;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

public class OAuth2SamlCrudTest extends AbstractAuthOnCrudTest {

    private static final String TOKEN_ENDPOINT = "/oauth2/token";
    private static final String ACCESS_TOKEN = "oauth2-saml-crud-token";

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
    protected ClassHandlerConnectorBase createConnector() {
        var config = new Config(wireMockServer.port(),
                "http://localhost:" + wireMockServer.port() + TOKEN_ENDPOINT,
                "crud-client", "crud-issuer", privateKeyPem);
        var connector = new TestConnector();
        connector.init(config);
        return connector;
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
        return pattern.withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN));
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
