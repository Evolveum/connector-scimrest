/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.auth.crud;

import com.evolveum.polygon.scimrest.ClassHandlerConnectorBase;
import com.evolveum.polygon.scimrest.support.AbstractCrudConnectorTest;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import org.testng.annotations.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.assertEquals;

/**
 * Verifies that authorization headers are propagated to CRUD operations, not just connector.test().
 *
 * Each subclass provides a connector configured with a specific auth method and declares
 * which request pattern to expect. The tests assert that search, create, and update requests
 * all carry the expected auth header.
 */
public abstract class AbstractAuthOnCrudTest extends AbstractCrudConnectorTest {

    protected abstract ClassHandlerConnectorBase createConnector();

    protected abstract void stubAuthPrerequisites();

    protected abstract RequestPatternBuilder withExpectedAuth(RequestPatternBuilder pattern);

    @Test
    public void testAuthOnSearch() {
        stubAuthPrerequisites();
        stubSearchAccounts();

        searchAccounts(createConnector());

        assertEquals(wireMockServer.findAll(
                withExpectedAuth(getRequestedFor(urlEqualTo(ACCOUNTS_PATH)))).size(), 1);
    }

    @Test
    public void testAuthOnCreate() {
        stubAuthPrerequisites();
        stubCreateAccount();

        createAccount(createConnector());

        assertEquals(wireMockServer.findAll(
                withExpectedAuth(postRequestedFor(urlEqualTo(ACCOUNTS_PATH)))).size(), 1);
    }

    @Test
    public void testAuthOnUpdate() {
        stubAuthPrerequisites();
        stubUpdateAccount();

        updateAccount(createConnector());

        assertEquals(wireMockServer.findAll(
                withExpectedAuth(putRequestedFor(urlPathMatching(ACCOUNTS_PATTERN)))).size(), 1);
    }
}
