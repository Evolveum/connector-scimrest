package com.evolveum.polygon.scimrest.exception;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.testng.Assert.*;

public class WireMockPortTest {

    private WireMockServer wireMockServer;

    @AfterMethod
    public void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
            wireMockServer.resetAll();
        }
    }

    @Test
    public void testWireMockPortIsCorrect() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        int port = wireMockServer.port();
        System.err.println("WireMock started on port: " + port);

        // Register a stub
        wireMockServer.stubFor(get(urlEqualTo("/port-test"))
            .willReturn(aResponse().withStatus(200).withBody("OK")));

        // Make request
        var client = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/port-test"))
            .GET()
            .build();
        
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.err.println("Response status: " + response.statusCode());
            System.err.println("Response body: " + response.body());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        
        // Check WireMock saw the request
        int count = wireMockServer.findAll(getRequestedFor(urlEqualTo("/port-test"))).size();
        System.err.println("WireMock request count: " + count);
        
        assertEquals(count, 1, "Expected 1 request to WireMock");
    }
}
