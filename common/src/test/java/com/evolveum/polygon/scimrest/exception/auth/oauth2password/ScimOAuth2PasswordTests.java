/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.exception.auth.oauth2password;

import org.identityconnectors.common.security.GuardedString;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.*;

public class ScimOAuth2PasswordTests extends AbstractScimOAuth2PasswordTests {

    private static final String TOKEN_ENDPOINT = "/oauth2/token";

    @BeforeMethod
    public void setUp() {
        setUpWireMock();
    }

    @AfterMethod
    public void tearDown() {
        tearDownWireMock();
    }

    @Test
    public void testTokenFetchedAndSentInScimRequests() {
        stubTokenEndpoint(TOKEN_ENDPOINT, "scim-pw-token", 3600);
        stubScimEndpoints("scim-pw-token");

        createScimConnector(TOKEN_ENDPOINT, "test-client", "alice",
                new GuardedString("secret".toCharArray())).schema();

        assertEquals(wireMockServer.findAll(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))).size(), 1);
        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(SCHEMAS_ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer scim-pw-token"))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 3);
    }

    @Test
    public void testTokenReusedOnSubsequentScimCalls() {
        stubTokenEndpoint(TOKEN_ENDPOINT, "reused-scim-pw-token", 3600);
        stubScimEndpoints("reused-scim-pw-token");

        var connector = createScimConnector(TOKEN_ENDPOINT, "test-client", "alice",
                new GuardedString("secret".toCharArray()));
        connector.schema();
        connector.schema();

        assertEquals(wireMockServer.findAll(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 3);
    }

    @Test
    public void testTokenRequestContainsCorrectFormParameters() {
        stubTokenEndpoint(TOKEN_ENDPOINT, "param-token", 3600);
        stubScimEndpoints("param-token");

        createScimConnector(TOKEN_ENDPOINT, "my-scim-client", "john.doe",
                new GuardedString("p@ssw0rd".toCharArray())).schema();

        assertEquals(wireMockServer.findAll(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))
                .withRequestBody(containing("grant_type=password"))
                .withRequestBody(containing("client_id=my-scim-client"))
                .withRequestBody(containing("username=john.doe"))
                .withRequestBody(containing("password=p%40ssw0rd"))).size(), 1);
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 3);
    }

    @Test
    public void testScopeIsSentInTokenRequest() {
        stubTokenEndpoint(TOKEN_ENDPOINT, "scoped-scim-token", 3600);
        stubScimEndpoints("scoped-scim-token");

        var config = new ScimOAuth2PasswordTestConfig(wireMockServer.port(), TOKEN_ENDPOINT,
                "client-id", "alice", new GuardedString("pw".toCharArray()));
        config.setScope("openid profile");
        var connector = new ScimOAuth2PasswordTestConnector(null);
        connector.init(config);
        connector.schema();

        assertEquals(wireMockServer.findAll(postRequestedFor(urlEqualTo(TOKEN_ENDPOINT))
                .withRequestBody(containing("scope=openid+profile"))).size(), 1);
    }

    @Test
    public void testConnectorFailsWhenTokenEndpointReturns401() {
        stubTokenEndpointError(TOKEN_ENDPOINT, 401);

        try {
            createScimConnector(TOKEN_ENDPOINT, "bad-client", "alice",
                    new GuardedString("wrong".toCharArray())).schema();
            fail("Expected ConnectorException was not thrown");
        } catch (Exception e) {
            assertTrue(e instanceof org.identityconnectors.framework.common.exceptions.ConnectorException,
                    "Expected ConnectorException but got: " + e.getClass().getName());
        }
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 1);
    }
}
