/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.exception;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

abstract class AbstractOAuth2Tests extends WireMockTestSupport {

    protected void stubTokenEndpoint(String url, String accessToken, int expiresIn) {
        stubTokenEndpoint(url, accessToken, expiresIn, null, null);
    }

    protected void stubTokenEndpoint(String url, String accessToken, int expiresIn,
                                     String grantType, String refreshToken) {
        var stub = post(urlEqualTo(url));
        if (grantType != null) {
            stub = stub.withRequestBody(containing("grant_type=" + grantType));
        }
        String body = "{\"access_token\":\"" + accessToken + "\",\"expires_in\":" + expiresIn
                + (refreshToken != null ? ",\"refresh_token\":\"" + refreshToken + "\"" : "") + "}";
        wireMockServer.stubFor(stub.willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(body)));
    }

    protected void stubTokenEndpointWithStatus(String url, int status, String accessToken) {
        String body = status == 200
                ? "{\"access_token\":\"" + accessToken + "\",\"expires_in\":3600}"
                : "{\"error\":\"invalid_request\"}";
        wireMockServer.stubFor(post(urlEqualTo(url))
                .willReturn(aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    protected void stubTokenEndpointError(String url, int errorStatus) {
        stubTokenEndpointError(url, errorStatus, null);
    }

    protected void stubTokenEndpointError(String url, int errorStatus, String grantType) {
        var stub = post(urlEqualTo(url));
        if (grantType != null) {
            stub = stub.withRequestBody(containing("grant_type=" + grantType));
        }
        wireMockServer.stubFor(stub.willReturn(aResponse()
                .withStatus(errorStatus)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":\"oauth2_error\"}")));
    }

    protected void stubTokenEndpointMatchingBody(String url, String bodyContains,
                                                 String accessToken, int expiresIn) {
        wireMockServer.stubFor(post(urlEqualTo(url))
                .withRequestBody(containing(bodyContains))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"" + accessToken + "\",\"expires_in\":" + expiresIn + "}")));
    }

    protected void stubRestApiEndpoint(String url, String expectedToken) {
        wireMockServer.stubFor(get(urlEqualTo(url))
                .withHeader("Authorization", equalTo("Bearer " + expectedToken))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"ok\"}")));
    }
}
