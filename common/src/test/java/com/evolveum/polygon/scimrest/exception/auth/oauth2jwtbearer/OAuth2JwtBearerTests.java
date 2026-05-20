/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.exception.auth.oauth2jwtbearer;

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.ConnectorIOException;
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

public class OAuth2JwtBearerTests extends AbstractOAuth2JwtBearerTests {

    private static final String TOKEN_ENDPOINT = "/oauth2/token";
    private static final String API_ENDPOINT = "/api/resource";
    private static final String ACCESS_TOKEN = "test-access-token";
    private static final String CLIENT_ID = "test-client";

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

        wireMockServer.verify(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))
                .withRequestBody(containing("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer"))
                .withRequestBody(containing("client_id=" + CLIENT_ID))
                .withRequestBody(containing("assertion=")));

        wireMockServer.verify(getRequestedFor(urlEqualTo(API_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));

        String assertion = captureAssertion();
        String header = decodeJwtPart(assertion, 0);
        String payload = decodeJwtPart(assertion, 1);

        assertTrue(header.contains("\"alg\":\"RS256\""));
        assertTrue(header.contains("\"typ\":\"JWT\""));
        assertTrue(payload.contains("\"iss\":\"" + CLIENT_ID + "\""));
        assertTrue(payload.contains("\"sub\":\"" + CLIENT_ID + "\""));
        assertTrue(payload.contains("\"aud\":"));
        assertTrue(payload.contains("\"exp\":"));
        assertTrue(payload.contains("\"iat\":"));
        assertTrue(payload.contains("\"jti\":"));
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 2);
    }

    @Test
    public void testInvalidPrivateKeyThrowsException() {
        stubTokenEndpointWithStatus(TOKEN_ENDPOINT, 200, ACCESS_TOKEN);
        stubRestApiEndpoint(API_ENDPOINT, ACCESS_TOKEN);

        assertThrows(ConnectorException.class, () -> createConnector("not-a-valid-pem-key").test());
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 0);
    }

    @Test
    public void testTokenEndpointErrorThrowsException() {
        stubTokenEndpointWithStatus(TOKEN_ENDPOINT, 400, null);
        stubRestApiEndpoint(API_ENDPOINT, ACCESS_TOKEN);

        assertThrows(ConnectorIOException.class, () -> createConnector(keyPair.getPrivate()).test());
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 1);
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

    private OAuth2RestConnector createConnector(PrivateKey key) {
        return createConnector(toPem(key));
    }

    private OAuth2RestConnector createConnector(String pemKey) {
        var config = new BaseJwtBearerConfig(
                wireMockServer.port(), API_ENDPOINT,
                "http://localhost:" + wireMockServer.port() + TOKEN_ENDPOINT,
                CLIENT_ID, new GuardedString(pemKey.toCharArray()));
        var connector = new OAuth2RestConnector();
        connector.init(config);
        return connector;
    }

}
