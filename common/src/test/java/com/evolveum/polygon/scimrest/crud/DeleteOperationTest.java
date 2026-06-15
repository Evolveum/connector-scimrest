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
 * Pins the current state of the REST {@code delete { }} directive: the script loads, but
 * RestDeleteOperationBuilderImpl is a stub (its build() is never registered in
 * BaseOperationSupportBuilder), so the delete operation is reported as unsupported and no
 * HTTP request is made.
 *
 * When delete support is implemented, this test must be replaced with one asserting the
 * DELETE request is sent to the configured endpoint.
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

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void deleteIsNotImplementedYet() {
        try {
            initConnector(SCRIPT).delete(new ObjectClass("Account"),
                    new Uid("123"),
                    new OperationOptionsBuilder().build());
        } finally {
            assertEquals(wireMockServer.findAll(deleteRequestedFor(anyUrl())).size(), 0);
        }
    }
}
