/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.crud;

import com.evolveum.polygon.scimrest.support.AbstractCrudConnectorTest;
import org.identityconnectors.framework.common.objects.AttributeDeltaBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.ConnectorObjectIdentification;
import org.identityconnectors.framework.common.objects.ConnectorObjectReference;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.expectThrows;

/**
 * Verifies the update operation with a PATCH endpoint: {@code endpoint(PATCH, ...)} sends
 * the changed attributes as a JSON body via the PATCH HTTP method.
 */
public class PatchUpdateTest extends AbstractCrudConnectorTest {

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
                    endpoint(PATCH, "accounts/{id}") {
                        request { contentType APPLICATION_JSON }
                    }
                }
            }
            """;

    private static final String COLLECTION_SCHEMA = """
            objectClass("Account") {
                attribute("id") { jsonType "string" }
                attribute("name") { jsonType "string" }
                attribute("tags") { jsonType "string"; multiValued true }
            }
            """;

    private static final String REFERENCE_SCHEMA = """
            objectClass("Account") {
                attribute("id") { jsonType "string" }
                attribute("name") { jsonType "string" }
                reference("members") {
                    objectClass "User"
                    multiValued true
                    json {
                        path attribute("_links").child("members")
                        referenceBy { uidFrom "href"; template "/users/{uid}"; nameFrom "title" }
                    }
                }
            }
            """;

    @Test
    public void updateIsSentAsPatchWithJsonBody() {
        wireMockServer.stubFor(get(urlEqualTo(ACCOUNT_BY_ID_PATH))
                .willReturn(okJson("{\"id\":\"123\",\"name\":\"old-name\"}")));
        wireMockServer.stubFor(patch(urlPathMatching(ACCOUNTS_PATTERN))
                .willReturn(okJson("{\"id\":\"123\",\"name\":\"new-name\"}")));

        initConnector(SCRIPT).updateDelta(new ObjectClass("Account"),
                new Uid("123"),
                Set.of(AttributeDeltaBuilder.build(Name.NAME, List.of("new-name"))),
                new OperationOptionsBuilder().build());

        assertEquals(wireMockServer.findAll(patchRequestedFor(urlEqualTo(ACCOUNT_BY_ID_PATH))
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(matchingJsonPath("$.name", equalTo("new-name")))).size(), 1);
    }

    @Test
    public void incrementalPatchReadsTheAffectedCollectionDespiteCallerProjection() {
        wireMockServer.stubFor(get(urlEqualTo(ACCOUNT_BY_ID_PATH))
                .willReturn(okJson("{\"id\":\"123\",\"name\":\"account\",\"tags\":[\"a\",\"b\"]}")));
        wireMockServer.stubFor(patch(urlEqualTo(ACCOUNT_BY_ID_PATH))
                .willReturn(okJson("{\"id\":\"123\",\"name\":\"account\",\"tags\":[\"a\",\"c\"]}")));
        var delta = new AttributeDeltaBuilder().setName("tags")
                .addValueToRemove("b").addValueToAdd("c").build();

        initConnector(COLLECTION_SCHEMA, CONNID_SCHEMA_SCRIPT, SCRIPT).updateDelta(
                new ObjectClass("Account"), new Uid("123"), Set.of(delta),
                new OperationOptionsBuilder().setAttributesToGet(Name.NAME).build());

        assertEquals(wireMockServer.findAll(patchRequestedFor(urlEqualTo(ACCOUNT_BY_ID_PATH))
                .withRequestBody(equalToJson("{\"tags\":[\"a\",\"c\"]}"))).size(), 1);
    }

    @Test
    public void missingOriginalCollectionRejectsIncrementalPatch() {
        wireMockServer.stubFor(get(urlEqualTo(ACCOUNT_BY_ID_PATH))
                .willReturn(okJson("{\"id\":\"123\",\"name\":\"account\"}")));
        var delta = new AttributeDeltaBuilder().setName("tags").addValueToAdd("c").build();

        expectThrows(ConnectorException.class, () -> initConnector(COLLECTION_SCHEMA, CONNID_SCHEMA_SCRIPT, SCRIPT)
                .updateDelta(new ObjectClass("Account"), new Uid("123"), Set.of(delta),
                        new OperationOptionsBuilder().build()));
        assertEquals(wireMockServer.findAll(patchRequestedFor(urlEqualTo(ACCOUNT_BY_ID_PATH))).size(), 0);
    }

    @Test
    public void incrementalPatchCanAddToAnEmptyCollection() {
        wireMockServer.stubFor(get(urlEqualTo(ACCOUNT_BY_ID_PATH))
                .willReturn(okJson("{\"id\":\"123\",\"name\":\"account\",\"tags\":[]}")));
        wireMockServer.stubFor(patch(urlEqualTo(ACCOUNT_BY_ID_PATH)).willReturn(aResponse().withStatus(204)));
        var delta = new AttributeDeltaBuilder().setName("tags").addValueToAdd("a").build();

        initConnector(COLLECTION_SCHEMA, CONNID_SCHEMA_SCRIPT, SCRIPT).updateDelta(
                new ObjectClass("Account"), new Uid("123"), Set.of(delta), new OperationOptionsBuilder().build());

        assertEquals(wireMockServer.findAll(patchRequestedFor(urlEqualTo(ACCOUNT_BY_ID_PATH))
                .withRequestBody(equalToJson("{\"tags\":[\"a\"]}"))).size(), 1);
    }

    @Test
    public void replacementCanClearAnOmittedCollection() {
        wireMockServer.stubFor(get(urlEqualTo(ACCOUNT_BY_ID_PATH))
                .willReturn(okJson("{\"id\":\"123\",\"name\":\"account\"}")));
        wireMockServer.stubFor(patch(urlEqualTo(ACCOUNT_BY_ID_PATH))
                .willReturn(okJson("{\"id\":\"123\",\"name\":\"account\",\"tags\":[]}")));
        initConnector(COLLECTION_SCHEMA, CONNID_SCHEMA_SCRIPT, SCRIPT).updateDelta(
                new ObjectClass("Account"), new Uid("123"), Set.of(AttributeDeltaBuilder.build("tags", List.of())),
                new OperationOptionsBuilder().build());
        assertEquals(wireMockServer.findAll(getRequestedFor(urlEqualTo(ACCOUNT_BY_ID_PATH))).size(), 1);
        assertEquals(wireMockServer.findAll(patchRequestedFor(urlEqualTo(ACCOUNT_BY_ID_PATH))
                .withRequestBody(equalToJson("{\"tags\":[]}"))).size(), 1);
    }

    @Test
    public void referenceRemovalMatchesUidWhenTheReadAlsoContainsAName() {
        wireMockServer.stubFor(get(urlEqualTo(ACCOUNT_BY_ID_PATH)).willReturn(okJson("""
                {"id":"123","name":"account","_links":{"members":[
                  {"href":"/users/11","title":"Alice"}, {"href":"/users/22","title":"Bob"}]}}
                """)));
        wireMockServer.stubFor(patch(urlEqualTo(ACCOUNT_BY_ID_PATH)).willReturn(aResponse().withStatus(204)));
        var reference = new ConnectorObjectReference(new ConnectorObjectIdentification(
                new ObjectClass("User"), Set.of(new Uid("11"))));
        var delta = new AttributeDeltaBuilder().setName("members").addValueToRemove(reference).build();

        initConnector(REFERENCE_SCHEMA, CONNID_SCHEMA_SCRIPT, SCRIPT).updateDelta(
                new ObjectClass("Account"), new Uid("123"), Set.of(delta),
                new OperationOptionsBuilder().setAttributesToGet(Name.NAME).build());

        assertEquals(wireMockServer.findAll(patchRequestedFor(urlEqualTo(ACCOUNT_BY_ID_PATH))
                .withRequestBody(equalToJson("{\"_links\":{\"members\":[{\"href\":\"/users/22\"}]}}"))).size(), 1);
    }
}
