/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.exception.auth.crud;

import com.evolveum.polygon.scimrest.ClassHandlerConnectorBase;
import com.evolveum.polygon.scimrest.exception.WireMockTestSupport;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import org.identityconnectors.framework.common.objects.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.assertEquals;

/**
 * Verifies that authorization headers are propagated to CRUD operations, not just connector.test().
 *
 * Each subclass provides a connector configured with a specific auth method and declares
 * which request pattern to expect. The tests assert that search, create, and update requests
 * all carry the expected auth header.
 */
public abstract class AbstractAuthOnCrudTest extends WireMockTestSupport {

    protected static final String ACCOUNTS_PATH = "/accounts";
    protected static final String ACCOUNTS_PATTERN = "/accounts/.*";
    protected static final String ACCOUNT_BY_ID_PATH = "/accounts/123";

    protected static final String SCHEMA_SCRIPT = """
            objectClass("Account") {
                attribute("id") { jsonType "string" }
                connIdAttribute("UID", "id")
                attribute("name") { jsonType "string" }
                connIdAttribute("NAME", "name")
            }
            """;

    protected static final String OPERATION_SCRIPT = """
            objectClass("Account") {
                search {
                    endpoint("accounts") { emptyFilterSupported true }
                    endpoint("accounts/{id}") {
                        singleResult()
                        supportedFilter(attribute("id").eq().anySingleValue()) {
                            request.pathParameter("id", value)
                        }
                    }
                }
                create {
                    endpoint("accounts") { }
                }
                update {
                    endpoint(PUT, "accounts/{id}") {
                        request { contentType APPLICATION_JSON }
                    }
                }
            }
            """;

    protected static class SimpleConfig extends BaseTestConfiguration {
        SimpleConfig(int port) {
            super(port);
        }

        @Override
        public String getRestTestEndpoint() {
            return null;
        }
    }

    @BeforeMethod
    public void setUp() throws Exception {
        setUpWireMock();
    }

    @AfterMethod
    public void tearDown() {
        tearDownWireMock();
    }

    protected abstract ClassHandlerConnectorBase createConnector();

    protected abstract void stubAuthPrerequisites();

    protected abstract RequestPatternBuilder withExpectedAuth(RequestPatternBuilder pattern);

    @Test
    public void testAuthOnSearch() {
        stubAuthPrerequisites();
        wireMockServer.stubFor(get(urlEqualTo(ACCOUNTS_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        createConnector().executeQuery(new ObjectClass("Account"), null, r -> true, new OperationOptionsBuilder().build());

        assertEquals(wireMockServer.findAll(
                withExpectedAuth(getRequestedFor(urlEqualTo(ACCOUNTS_PATH)))).size(), 1);
    }

    @Test
    public void testAuthOnCreate() {
        stubAuthPrerequisites();
        wireMockServer.stubFor(post(urlEqualTo(ACCOUNTS_PATH))
                .willReturn(aResponse().withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"123\",\"name\":\"created\"}")));

        createConnector().create(new ObjectClass("Account"),
                Set.of(AttributeBuilder.build("name", "test")),
                new OperationOptionsBuilder().build());

        assertEquals(wireMockServer.findAll(
                withExpectedAuth(postRequestedFor(urlEqualTo(ACCOUNTS_PATH)))).size(), 1);
    }

    @Test
    public void testAuthOnUpdate() {
        stubAuthPrerequisites();
        wireMockServer.stubFor(get(urlEqualTo(ACCOUNT_BY_ID_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"123\",\"name\":\"test-account\"}")));
        wireMockServer.stubFor(put(urlPathMatching(ACCOUNTS_PATTERN))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"123\",\"name\":\"updated\"}")));

        createConnector().updateDelta(new ObjectClass("Account"),
                new Uid("123"),
                Set.of(AttributeDeltaBuilder.build("name", List.of("updated"), null)),
                new OperationOptionsBuilder().build());

        assertEquals(wireMockServer.findAll(
                withExpectedAuth(putRequestedFor(urlPathMatching(ACCOUNTS_PATTERN)))).size(), 1);
    }
}
