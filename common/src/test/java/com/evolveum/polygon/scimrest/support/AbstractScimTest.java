/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.support;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

/**
 * Stubs SCIM discovery ({@code /Schemas}, {@code /ResourceTypes}) - boilerplate every SCIM CRUD
 * test needs before it can exercise anything else, previously copy-pasted per test. Not tied to
 * any one resource type: {@link #stubDiscovery} takes the resource name, its endpoint and the
 * schema body; {@link #stubUserDiscovery} is a convenience for the overwhelmingly common "User"
 * mapped to {@code /Users} case that most tests in this suite actually need.
 */
public abstract class AbstractScimTest extends WireMockTestSupport {

    protected static final String SCIM_BASE_PATH = "/scim";
    protected static final String SCHEMAS_ENDPOINT = SCIM_BASE_PATH + "/Schemas";
    protected static final String RESOURCE_TYPES_ENDPOINT = SCIM_BASE_PATH + "/ResourceTypes";
    protected static final String USERS_ENDPOINT = SCIM_BASE_PATH + "/Users";

    /** Stubs {@link #SCHEMAS_ENDPOINT} with {@code schemasResponse} and {@link #RESOURCE_TYPES_ENDPOINT} with {@code resourceName} mapped to {@code endpoint}. */
    protected void stubDiscovery(String resourceName, String endpoint, String schemasResponse) {
        wireMockServer.stubFor(get(urlEqualTo(SCHEMAS_ENDPOINT))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/scim+json")
                        .withBody(schemasResponse)));
        wireMockServer.stubFor(get(urlEqualTo(RESOURCE_TYPES_ENDPOINT))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/scim+json")
                        .withBody("""
                                {
                                  "schemas": ["urn:ietf:params:scim:api:messages:2.0:ListResponse"],
                                  "totalResults": 1,
                                  "Resources": [
                                    {
                                      "schemas": ["urn:ietf:params:scim:schemas:core:2.0:ResourceType"],
                                      "id": "%1$s",
                                      "name": "%1$s",
                                      "endpoint": "%2$s",
                                      "schema": "urn:ietf:params:scim:schemas:core:2.0:%1$s"
                                    }
                                  ]
                                }
                                """.formatted(resourceName, endpoint))));
    }

    /** {@link #stubDiscovery(String, String, String)} for the "User" resource mapped to {@link #USERS_ENDPOINT}. */
    protected void stubUserDiscovery(String schemasResponse) {
        stubDiscovery("User", "/Users", schemasResponse);
    }
}
