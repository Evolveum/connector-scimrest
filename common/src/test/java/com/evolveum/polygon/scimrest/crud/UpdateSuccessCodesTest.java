/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.crud;

import com.evolveum.polygon.scimrest.support.AbstractCrudConnectorTest;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeDelta;
import org.identityconnectors.framework.common.objects.AttributeDeltaBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.Uid;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Verifies update endpoints against success responses that carry no body (HTTP 204 No Content,
 * HTTP 201 without a body) and against a 200 body carrying the updated object (issue #12508).
 * The connector must report the applied change set instead of failing with "No attributes set!".
 */
public class UpdateSuccessCodesTest extends AbstractCrudConnectorTest {

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
                create {
                    endpoint("accounts") {
                        request { contentType APPLICATION_JSON }
                    }
                }
                update {
                    endpoint(PUT, "accounts/{id}") {
                        request { contentType APPLICATION_JSON }
                    }
                }
            }
            """;

    private void stubReadById() {
        wireMockServer.stubFor(get(urlEqualTo(ACCOUNT_BY_ID_PATH))
                .willReturn(okJson("{\"id\":\"123\",\"name\":\"old-name\"}")));
    }

    private Set<AttributeDelta> updateName() {
        var delta = AttributeDeltaBuilder.build(Name.NAME, List.of("new-name"));
        return initConnector(SCRIPT).updateDelta(new ObjectClass("Account"),
                new Uid("123"),
                Set.of(delta),
                new OperationOptionsBuilder().build());
    }

    @Test
    public void putReturning204IsSuccessAndReturnsRequestedDeltas() {
        stubReadById();
        wireMockServer.stubFor(put(urlEqualTo(ACCOUNT_BY_ID_PATH))
                .willReturn(noContent()));

        var applied = updateName();

        assertEquals(applied, Set.of(AttributeDeltaBuilder.build(Name.NAME, List.of("new-name"))));
        assertEquals(wireMockServer.findAll(putRequestedFor(urlEqualTo(ACCOUNT_BY_ID_PATH))
                .withRequestBody(matchingJsonPath("$.name", equalTo("new-name")))).size(), 1);
    }

    @Test
    public void putReturning201WithoutBodyIsSuccess() {
        stubReadById();
        wireMockServer.stubFor(put(urlEqualTo(ACCOUNT_BY_ID_PATH))
                .willReturn(aResponse().withStatus(201)));

        var applied = updateName();

        assertEquals(applied, Set.of(AttributeDeltaBuilder.build(Name.NAME, List.of("new-name"))));
    }

    @Test
    public void putReturning200WithBodyReportsBodyValues() {
        stubReadById();
        wireMockServer.stubFor(put(urlEqualTo(ACCOUNT_BY_ID_PATH))
                .willReturn(okJson("{\"id\":\"123\",\"name\":\"FromServer\"}")));

        var applied = updateName();

        assertEquals(applied, Set.of(AttributeDeltaBuilder.build(Name.NAME, List.of("FromServer"))));
    }

    @Test
    public void createReturning201WithLocationHeaderIsSuccess() {
        wireMockServer.stubFor(post(urlEqualTo(ACCOUNTS_PATH))
                .willReturn(aResponse().withStatus(201)
                        .withHeader("Location",
                                "http://localhost:" + wireMockServer.port() + ACCOUNTS_PATH + "/456")));

        var uid = initConnector(SCRIPT).create(new ObjectClass("Account"),
                Set.of(AttributeBuilder.build(Name.NAME, "test")),
                new OperationOptionsBuilder().build());

        assertEquals(uid, new Uid("456"));
    }

    @Test
    public void createReturning201WithoutBodyOrLocationFails() {
        wireMockServer.stubFor(post(urlEqualTo(ACCOUNTS_PATH))
                .willReturn(aResponse().withStatus(201)));

        ConnectorException ex = null;
        try {
            initConnector(SCRIPT).create(new ObjectClass("Account"),
                    Set.of(AttributeBuilder.build(Name.NAME, "test")),
                    new OperationOptionsBuilder().build());
            fail("expected a ConnectorException");
        } catch (ConnectorException e) {
            ex = e;
        }
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("Location"));
    }
}
