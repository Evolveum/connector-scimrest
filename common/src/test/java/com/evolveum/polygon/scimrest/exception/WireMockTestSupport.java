/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.exception;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.testng.Assert;

public class WireMockTestSupport {

    protected WireMockServer wireMockServer;

    protected void setUpWireMock() {
        wireMockServer = new WireMockServer();
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    protected void tearDownWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
            wireMockServer.resetAll();
        }
    }
}

