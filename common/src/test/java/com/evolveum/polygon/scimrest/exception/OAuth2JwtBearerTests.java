/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.exception;

import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.*;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.ConnectorIOException;
import org.identityconnectors.framework.common.exceptions.ConnectionFailedException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.Base64;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.*;

public class OAuth2JwtBearerTests extends AbstractOAuth2Tests {

    private static final String TOKEN_ENDPOINT = "/oauth2/token";
    private static final String API_ENDPOINT   = "/api/resource";
    private static final String ACCESS_TOKEN   = "test-access-token";
    private static final String CLIENT_ID      = "test-client";

    private KeyPair keyPair;

    @BeforeMethod
    public void setUp() throws Exception {
        setUpWireMock();
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keyPair = gen.generateKeyPair();
    }

    @AfterMethod
    public void tearDown() {
        tearDownWireMock();
    }

    @Test
    public void testJwtBearerFlow() {
        stubTokenEndpointWithStatus(TOKEN_ENDPOINT, 200, ACCESS_TOKEN);
        stubRestApiEndpoint(API_ENDPOINT, ACCESS_TOKEN);

        createConnector(keyPair.getPrivate()).test();

        // token endpoint dostal JWT Bearer assertion so spravnymi parametrami
        wireMockServer.verify(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))
            .withRequestBody(containing("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer"))
            .withRequestBody(containing("client_id=" + CLIENT_ID))
            .withRequestBody(containing("assertion=")));

        // API bolo volane so ziskanym access tokenom
        wireMockServer.verify(getRequestedFor(urlEqualTo(API_ENDPOINT))
            .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));

        // JWT header a payload obsahuju spravne hodnoty
        String assertion = captureAssertion();
        String header  = decodeJwtPart(assertion, 0);
        String payload = decodeJwtPart(assertion, 1);

        assertTrue(header.contains("\"alg\":\"RS256\""));
        assertTrue(header.contains("\"typ\":\"JWT\""));
        assertTrue(payload.contains("\"iss\":\"" + CLIENT_ID + "\""));
        assertTrue(payload.contains("\"sub\":\"" + CLIENT_ID + "\""));
        assertTrue(payload.contains("\"aud\":"));
        assertTrue(payload.contains("\"exp\":"));
        assertTrue(payload.contains("\"iat\":"));
        assertTrue(payload.contains("\"jti\":"));
    }

    @Test
    public void testInvalidPrivateKeyThrowsException() {
        stubTokenEndpointWithStatus(TOKEN_ENDPOINT, 200, ACCESS_TOKEN);
        stubRestApiEndpoint(API_ENDPOINT, ACCESS_TOKEN);

        var connector = createConnector("not-a-valid-pem-key");
        assertThrows(ConnectorException.class, connector::test);
    }

    @Test
    public void testTokenEndpointErrorThrowsException() {
        stubTokenEndpointWithStatus(TOKEN_ENDPOINT, 400, null);
        stubRestApiEndpoint(API_ENDPOINT, ACCESS_TOKEN);

        assertThrows(ConnectorIOException.class, () -> createConnector(keyPair.getPrivate()).test());
    }

    @Test
    public void testUnknownGrantTypeThrowsException() {
        stubTokenEndpointWithStatus(TOKEN_ENDPOINT, 200, ACCESS_TOKEN);
        stubRestApiEndpoint(API_ENDPOINT, ACCESS_TOKEN);

        var config = new JwtTestConfiguration(wireMockServer.port(),
                new GuardedString(toPem(keyPair.getPrivate()).toCharArray()), "unknown_grant");
        var connector = new JwtTestConnector(config);
        connector.init(config);

        assertThrows(ConnectionFailedException.class, connector::test);
    }

    // --- helpers ---

    private String captureAssertion() {
        var requests = wireMockServer.findAll(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT)));
        assertEquals(requests.size(), 1);
        for (String param : requests.get(0).getBodyAsString().split("&")) {
            if (param.startsWith("assertion=")) {
                return URLDecoder.decode(param.substring("assertion=".length()), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("No assertion in request body");
    }

    private String decodeJwtPart(String jwt, int index) {
        return new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[index]), StandardCharsets.UTF_8);
    }

    private JwtTestConnector createConnector(PrivateKey key) {
        return createConnector(toPem(key));
    }

    private JwtTestConnector createConnector(String pemKey) {
        var config = new JwtTestConfiguration(wireMockServer.port(),
                new GuardedString(pemKey.toCharArray()), "jwt_bearer");
        var connector = new JwtTestConnector(config);
        connector.init(config);
        return connector;
    }

    private static String toPem(PrivateKey key) {
        String encoded = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                .encodeToString(key.getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + encoded + "\n-----END PRIVATE KEY-----\n";
    }

    // --- test doubles ---

    private static class JwtTestConfiguration extends BaseRestGroovyConnectorConfiguration
            implements RestClientConfiguration.OAuth2Authorization {

        private final int port;
        private final GuardedString privateKey;
        private final String grantType;

        JwtTestConfiguration(int port, GuardedString privateKey, String grantType) {
            this.port = port;
            this.privateKey = privateKey;
            this.grantType = grantType;
        }

        @Override public String getBaseAddress()                    { return "http://localhost:" + port; }
        @Override public String getRestTestEndpoint()               { return API_ENDPOINT; }
        @Override public String getRestOAuth2TokenUrl()             { return "http://localhost:" + port + TOKEN_ENDPOINT; }
        @Override public String getRestOAuth2ClientId()             { return CLIENT_ID; }
        @Override public GuardedString getRestOAuth2ClientSecret()  { return null; }
        @Override public String getRestOAuth2GrantType()            { return grantType; }
        @Override public GuardedString getRestOAuth2PrivateKey()    { return privateKey; }
    }

    private static class JwtTestConnector extends AbstractGroovyRestConnector<BaseRestGroovyConnectorConfiguration> {
        private final BaseRestGroovyConnectorConfiguration config;

        JwtTestConnector(BaseRestGroovyConnectorConfiguration config) { this.config = config; }

        @Override protected void initializeSchema(GroovySchemaLoader loader) {}
        @Override protected void initializeAuthorizationHandler(GroovyRestHandlerBuilder builder) {}
        @Override protected void initializeObjectClassHandler(GroovyRestHandlerBuilder builder) {}
    }
}
