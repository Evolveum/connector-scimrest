/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.crud;

import com.evolveum.polygon.scimrest.support.AbstractCrudConnectorTest;
import org.identityconnectors.framework.common.exceptions.ConnectionFailedException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.InvalidCredentialException;
import org.identityconnectors.framework.common.exceptions.PermissionDeniedException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.Uid;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

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

    @DataProvider
    public Object[][] successfulResponses() {
        return new Object[][] {
                {200, ""},
                {202, ""},
                {204, ""},
                {202, "null"},
                {200, "{\"deleted\":true}"}
        };
    }

    @Test(dataProvider = "successfulResponses")
    public void deleteAcceptsSuccessfulResponse(int status, String body) {
        wireMockServer.stubFor(delete(urlEqualTo(ACCOUNT_BY_ID_PATH))
                .willReturn(aResponse().withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));

        initConnector(SCRIPT).delete(new ObjectClass("Account"),
                new Uid("123"),
                new OperationOptionsBuilder().build());

        assertEquals(wireMockServer.findAll(deleteRequestedFor(urlEqualTo(ACCOUNT_BY_ID_PATH))).size(), 1);
    }

    @DataProvider
    public Object[][] failedResponses() {
        return new Object[][] {
                {401, InvalidCredentialException.class},
                {403, PermissionDeniedException.class},
                {404, UnknownUidException.class},
                {500, ConnectionFailedException.class}
        };
    }

    @Test(dataProvider = "failedResponses")
    public void deletePreservesErrorTypeAndDetail(int status, Class<? extends ConnectorException> exceptionType) {
        wireMockServer.stubFor(delete(urlEqualTo(ACCOUNT_BY_ID_PATH))
                .willReturn(aResponse().withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"detail\":\"Cannot delete account\"}")));

        var connector = initConnector(SCRIPT);
        var exception = expectThrows(exceptionType, () -> connector.delete(new ObjectClass("Account"),
                new Uid("123"),
                new OperationOptionsBuilder().build()));

        assertTrue(exception.getMessage().contains("HTTP " + status), exception.getMessage());
        assertTrue(exception.getMessage().contains(ACCOUNT_BY_ID_PATH), exception.getMessage());
        assertTrue(exception.getMessage().contains("Cannot delete account"), exception.getMessage());
    }
}
