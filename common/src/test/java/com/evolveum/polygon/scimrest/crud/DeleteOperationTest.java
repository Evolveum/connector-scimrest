/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.crud;

import com.evolveum.polygon.scimrest.support.AbstractCrudConnectorTest;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.Uid;
import org.testng.annotations.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.assertEquals;

/**
 * Verifies the REST {@code delete { }} directive: a DELETE request is sent to the configured endpoint
 * with the UID substituted into the {@code {id}} path parameter.
 */
public class DeleteOperationTest extends AbstractCrudConnectorTest {

    private static final String SCRIPT = """
            objectClass("Account") {
                search {
                    endpoint("accounts") { emptyFilterSupported true }
                }
                delete {
                    endpoint(DELETE, "accounts/{id}")
                }
            }
            """;

    @Test
    public void deleteSendsDeleteRequestToEndpoint() {
        wireMockServer.stubFor(delete(urlEqualTo(ACCOUNT_BY_ID_PATH))
                .willReturn(aResponse().withStatus(204)));

        initConnector(SCRIPT).delete(new ObjectClass("Account"),
                new Uid("123"),
                new OperationOptionsBuilder().build());

        assertEquals(wireMockServer.findAll(deleteRequestedFor(urlEqualTo(ACCOUNT_BY_ID_PATH))).size(), 1);
    }
}
