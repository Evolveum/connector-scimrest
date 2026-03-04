/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.exception;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.*;

/**
 * Simple test to verify WireMock is working
 */
public class WireMockSmokeTest {

    private WireMockServer wireMockServer;

    @AfterMethod
    public void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
            wireMockServer.resetAll();
        }
    }

    @Test
    public void testWireMockReceivesRequest() {
        wireMockServer = new WireMockServer();
        wireMockServer.start();
        int port = wireMockServer.port();
        System.err.println("WireMock port: " + port);

        wireMockServer.stubFor(get(urlEqualTo("/smoke-test"))
            .willReturn(aResponse().withStatus(200).withBody("OK")));

        // Make a request using Java's HttpClient
        var client = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/smoke-test"))
            .GET()
            .build();
        
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.err.println("Response status: " + response.statusCode());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        
        int count = wireMockServer.findAll(getRequestedFor(urlEqualTo("/smoke-test"))).size();
        System.err.println("Request count: " + count);
        
        assertEquals(count, 1, "WireMock should have received 1 request");
    }
}
