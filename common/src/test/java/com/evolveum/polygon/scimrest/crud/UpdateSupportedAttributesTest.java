/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.crud;

import com.evolveum.polygon.scimrest.support.AbstractCrudConnectorTest;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.AttributeDeltaBuilder;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.Uid;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.assertEquals;

/**
 * Verifies the {@code supportedAttributes} update directive: each endpoint declares which
 * attributes it can update, and deltas are dispatched to the endpoint supporting them.
 * Deltas no endpoint supports are rejected.
 */
public class UpdateSupportedAttributesTest extends AbstractCrudConnectorTest {

    private static final String SCHEMA = """
            objectClass("Account") {
                attribute("id") { jsonType "string" }
                attribute("name") { jsonType "string" }
                attribute("displayName") { jsonType "string" }
                attribute("status") { jsonType "string" }
            }
            """;

    private static final String SCRIPT = """
            objectClass("Account") {
                search {
                    endpoint("accounts/{id}") {
                        singleResult()
                        supportedFilter(attribute("id").eq().anySingleValue()) {
                            request.pathParameter("id", value)
                        }
                    }
                }
                update {
                    endpoint(PUT, "accounts/{id}") {
                        request { contentType APPLICATION_JSON }
                        supportedAttributes "displayName"
                    }
                    endpoint(POST, "accounts/{id}/activate") {
                        supportedAttributes "status"
                    }
                }
            }
            """;

    private void stubReadById() {
        wireMockServer.stubFor(get(urlEqualTo(ACCOUNT_BY_ID_PATH))
                .willReturn(okJson("{\"id\":\"123\",\"name\":\"acc\",\"displayName\":\"Acc\",\"status\":\"inactive\"}")));
    }

    private void update(String attribute, String value) {
        initConnector(SCHEMA, CONNID_SCHEMA_SCRIPT, SCRIPT).updateDelta(new ObjectClass("Account"),
                new Uid("123"),
                Set.of(AttributeDeltaBuilder.build(attribute, List.of(value), null)),
                new OperationOptionsBuilder().build());
    }

    @Test
    public void deltaIsDispatchedToEndpointSupportingTheAttribute() {
        stubReadById();
        wireMockServer.stubFor(post(urlEqualTo(ACCOUNT_BY_ID_PATH + "/activate"))
                .willReturn(okJson("{\"id\":\"123\",\"name\":\"acc\",\"status\":\"active\"}")));

        update("status", "active");

        assertEquals(wireMockServer.findAll(postRequestedFor(urlEqualTo(ACCOUNT_BY_ID_PATH + "/activate"))).size(), 1);
        assertEquals(wireMockServer.findAll(putRequestedFor(urlPathMatching(ACCOUNTS_PATTERN))).size(), 0);
    }

    @Test
    public void otherAttributeGoesToOtherEndpoint() {
        stubReadById();
        wireMockServer.stubFor(put(urlEqualTo(ACCOUNT_BY_ID_PATH))
                .willReturn(okJson("{\"id\":\"123\",\"name\":\"acc\",\"displayName\":\"Renamed\"}")));

        update("displayName", "Renamed");

        assertEquals(wireMockServer.findAll(putRequestedFor(urlEqualTo(ACCOUNT_BY_ID_PATH))
                .withRequestBody(matchingJsonPath("$.displayName", equalTo("Renamed")))).size(), 1);
        assertEquals(wireMockServer.findAll(postRequestedFor(urlPathMatching(ACCOUNTS_PATTERN))).size(), 0);
    }

    @Test(expectedExceptions = ConnectorException.class)
    public void unsupportedDeltaIsRejected() {
        stubReadById();

        update("name", "other");
    }
}
