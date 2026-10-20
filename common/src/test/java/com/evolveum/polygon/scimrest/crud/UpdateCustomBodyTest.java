/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.crud;

import com.evolveum.polygon.scimrest.support.AbstractCrudConnectorTest;
import org.identityconnectors.framework.common.objects.AttributeDeltaBuilder;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.Uid;
import org.testng.annotations.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.assertEquals;

/**
 * An update endpoint builds its own request body via a script instead of relying solely on the
 * framework's supported-attributes serialization - the wire payload a target API expects
 * frequently diverges from a plain per-attribute JSON object: a different field name, a value
 * not present in the delta at all, or a shape that depends on which kind of change
 * (add/remove/replace) is being sent. These tests cover that behaviour across all three write
 * methods (PUT, PATCH, POST), each through a real third-party API shape.
 *
 * <p>Schema: the shared {@code Account} object class (id/name) plus {@code email}, {@code status}
 * and a multivalued {@code members} attribute, modeling a user profile with a group membership
 * list.</p>
 */
public class UpdateCustomBodyTest extends AbstractCrudConnectorTest {

    private static final String SCHEMA = """
            objectClass("Account") {
                attribute("id") { jsonType "string" }
                attribute("name") { jsonType "string" }
                attribute("email") { jsonType "string" }
                attribute("status") { jsonType "string" }
                attribute("members") { jsonType "string"; multiValued true }
            }
            """;

    private static final JsonMapper MAPPER = new JsonMapper();

    private void stubReadById(String email, String status) {
        wireMockServer.stubFor(get(urlEqualTo(ACCOUNT_BY_ID_PATH))
                .willReturn(okJson("""
                        {"id":"123","name":"acc","email":"%s","status":"%s","members":[]}
                        """.formatted(email, status))));
    }

    private static ObjectNode requestBodyJson(com.github.tomakehurst.wiremock.verification.LoggedRequest request) {
        return (ObjectNode) MAPPER.readTree(request.getBody());
    }

    // ------------------------------------------------------------------------------------------
    // PUT - the target requires the complete representation of the resource, so the unchanged
    // fields have to come from somewhere other than the delta: req.before().
    // ------------------------------------------------------------------------------------------

    /**
     * Models Okta's "Update user": {@code PUT /api/v1/users/{userId}} replaces the entire
     * {@code profile} object - any field missing from the body is cleared on the target. Only
     * {@code status} changed in this delta, but {@code name} and {@code email} are still sent,
     * carried over from {@code req.before()}.
     *
     * @see <a href="https://developer.okta.com/docs/reference/api/users/#update-user">Okta - Update user</a>
     */
    @Test
    public void putEndpointSendsTheCompleteMergedObject() {
        stubReadById("old@example.com", "active");
        wireMockServer.stubFor(put(urlEqualTo(ACCOUNT_BY_ID_PATH))
                .willReturn(aResponse().withStatus(200)));

        var script = """
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
                            request {
                                contentType APPLICATION_JSON
                                body { req ->
                                    def before = req.before()
                                    def name = before?.getAttributeByName("name")?.getValue()?.first()
                                    def email = before?.getAttributeByName("email")?.getValue()?.first()
                                    def delta = req.attributeDeltaSet().find { it.name == "status" }
                                    def status = delta?.valuesToReplace?.first()
                                    '''
                                    {
                                      "name": "%s",
                                      "email": "%s",
                                      "status": "%s"
                                    }
                                    '''.formatted(name, email, status).getBytes("UTF-8")
                                }
                            }
                            supportedAttributes "status"
                        }
                    }
                }
                """;

        initConnector(SCHEMA, CONNID_SCHEMA_SCRIPT, script).updateDelta(new ObjectClass("Account"), new Uid("123"),
                Set.of(AttributeDeltaBuilder.build("status", List.of("suspended"))),
                new OperationOptionsBuilder().build());

        var requests = wireMockServer.findAll(putRequestedFor(urlEqualTo(ACCOUNT_BY_ID_PATH)));
        assertEquals(requests.size(), 1);
        var sent = requestBodyJson(requests.getFirst());
        assertEquals(sent.get("name").asString(), "acc");
        assertEquals(sent.get("email").asString(), "old@example.com");
        assertEquals(sent.get("status").asString(), "suspended");
    }

    // ------------------------------------------------------------------------------------------
    // PATCH - only the fields that actually changed belong in the body; req.before() is not
    // needed at all, even when several attributes change together in one updateDelta() call.
    // ------------------------------------------------------------------------------------------

    /**
     * Models GitHub's "Update a repository": {@code PATCH /repos/{owner}/{repo}} only requires
     * the fields being changed - anything omitted is left untouched on the target. Two attributes
     * change in the same {@code updateDelta()} call; both are sent in the one PATCH body, and
     * nothing else is.
     *
     * @see <a href="https://docs.github.com/en/rest/repos/repos#update-a-repository">GitHub - Update a repository</a>
     */
    @Test
    public void patchEndpointSendsOnlyTheChangedFields() {
        stubReadById("old@example.com", "active");
        wireMockServer.stubFor(patch(urlEqualTo(ACCOUNT_BY_ID_PATH))
                .willReturn(aResponse().withStatus(200)));

        var script = """
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
                            request {
                                contentType APPLICATION_JSON
                                body { req ->
                                    def fields = req.attributeDeltaSet().collectEntries {
                                        [(it.name): it.valuesToReplace?.first()]
                                    }
                                    '''
                                    {
                                      "email": "%s",
                                      "status": "%s"
                                    }
                                    '''.formatted(fields["email"], fields["status"]).getBytes("UTF-8")
                                }
                            }
                            supportedAttributes "email", "status"
                        }
                    }
                }
                """;

        initConnector(SCHEMA, CONNID_SCHEMA_SCRIPT, script).updateDelta(new ObjectClass("Account"), new Uid("123"),
                Set.of(
                        AttributeDeltaBuilder.build("email", List.of("new@example.com")),
                        AttributeDeltaBuilder.build("status", List.of("suspended"))),
                new OperationOptionsBuilder().build());

        var requests = wireMockServer.findAll(patchRequestedFor(urlEqualTo(ACCOUNT_BY_ID_PATH)));
        assertEquals(requests.size(), 1);
        var sent = requestBodyJson(requests.getFirst());
        assertEquals(sent.get("email").asString(), "new@example.com");
        assertEquals(sent.get("status").asString(), "suspended");
        assertEquals(sent.has("name"), false, "name did not change and was never read from before()");
    }

    // ------------------------------------------------------------------------------------------
    // POST - an action/membership endpoint; the wire field names and shape have nothing to do
    // with the declared attribute name, and the single-value and multi-value cases differ.
    // ------------------------------------------------------------------------------------------

    /**
     * Models Google Workspace Directory's "Add member to group":
     * {@code POST /admin/directory/v1/groups/{groupKey}/members} with body
     * {@code {"email": "...", "role": "MEMBER"}} - two fields, neither named after the ConnId
     * {@code members} attribute, and a value ({@code role}) that never comes from the delta at
     * all.
     *
     * @see <a href="https://developers.google.com/workspace/admin/directory/reference/rest/v1/members/insert">Google Workspace Directory - Add member to group</a>
     */
    @Test
    public void postEndpointAddingASingleMember_buildsCustomPayloadShape() {
        stubReadById("old@example.com", "active");
        wireMockServer.stubFor(post(urlEqualTo("/accounts/123/members"))
                .willReturn(aResponse().withStatus(201)));

        var script = """
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
                        endpoint(POST, "accounts/{id}/members") {
                            request {
                                contentType APPLICATION_JSON
                                body { req ->
                                    def delta = req.attributeDeltaSet().find { it.name == "members" }
                                    def email = delta?.valuesToAdd?.first()
                                    '''
                                    {
                                      "email": "%s",
                                      "role": "MEMBER"
                                    }
                                    '''.formatted(email).getBytes("UTF-8")
                                }
                            }
                            supportedAttributes "members"
                        }
                    }
                }
                """;

        initConnector(SCHEMA, CONNID_SCHEMA_SCRIPT, script).updateDelta(new ObjectClass("Account"), new Uid("123"),
                Set.of(AttributeDeltaBuilder.build("members", List.of("user-1@example.com"), null)),
                new OperationOptionsBuilder().build());

        var requests = wireMockServer.findAll(postRequestedFor(urlEqualTo("/accounts/123/members")));
        assertEquals(requests.size(), 1);
        var sent = requestBodyJson(requests.getFirst());
        assertEquals(sent.get("email").asString(), "user-1@example.com");
        assertEquals(sent.get("role").asString(), "MEMBER");
    }

    /**
     * Same Google endpoint, but the delta adds two members in one {@code updateDelta()} call.
     * The API accepts exactly one member per request, so the connector issues one POST per added
     * value - each with its own single-member body, same shape as the single-value case.
     */
    @Test
    public void postEndpointAddingMultipleMembersInOneDelta_needsOneRequestPerMember() {
        stubReadById("old@example.com", "active");
        wireMockServer.stubFor(post(urlEqualTo("/accounts/123/members"))
                .willReturn(aResponse().withStatus(201)));

        var script = """
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
                        endpoint(POST, "accounts/{id}/members") {
                            request {
                                contentType APPLICATION_JSON
                                body { req ->
                                    def delta = req.attributeDeltaSet().find { it.name == "members" }
                                    def email = delta?.valuesToAdd?.first()
                                    '''
                                    {
                                      "email": "%s",
                                      "role": "MEMBER"
                                    }
                                    '''.formatted(email).getBytes("UTF-8")
                                }
                            }
                            supportedAttributes "members"
                        }
                    }
                }
                """;

        initConnector(SCHEMA, CONNID_SCHEMA_SCRIPT, script).updateDelta(new ObjectClass("Account"), new Uid("123"),
                Set.of(AttributeDeltaBuilder.build("members", List.of("user-1@example.com", "user-2@example.com"), null)),
                new OperationOptionsBuilder().build());

        var requests = wireMockServer.findAll(postRequestedFor(urlEqualTo("/accounts/123/members")));
        assertEquals(requests.size(), 2, "one POST per added member, the API only accepts one member per call");
        var emails = requests.stream().map(r -> requestBodyJson(r).get("email").asString()).sorted().toList();
        assertEquals(emails, List.of("user-1@example.com", "user-2@example.com"));
    }

    /**
     * A single POST endpoint distinguishing add vs. remove entirely from the script, without any
     * new routing or dynamic query/path parameter support - mirrors APIs that use one membership
     * endpoint with an {@code op} field rather than Google's separate insert/delete endpoints. It
     * only needs what {@code UpdateRequest} already carries
     * ({@code AttributeDelta.getValuesToAdd()}/{@code getValuesToRemove()}).
     */
    @Test
    public void postEndpointDistinguishesAddFromRemove_inOneScript() {
        stubReadById("old@example.com", "active");
        wireMockServer.stubFor(post(urlEqualTo("/accounts/123/members"))
                .willReturn(aResponse().withStatus(200)));

        var script = """
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
                        endpoint(POST, "accounts/{id}/members") {
                            request {
                                contentType APPLICATION_JSON
                                body { req ->
                                    def delta = req.attributeDeltaSet().find { it.name == "members" }
                                    def added = delta?.valuesToAdd
                                    def removed = delta?.valuesToRemove
                                    if (added) {
                                        '''
                                        {
                                          "op": "add",
                                          "email": "%s",
                                          "role": "MEMBER"
                                        }
                                        '''.formatted(added.first()).getBytes("UTF-8")
                                    } else {
                                        '''
                                        {
                                          "op": "remove",
                                          "email": "%s",
                                          "role": "MEMBER"
                                        }
                                        '''.formatted(removed.first()).getBytes("UTF-8")
                                    }
                                }
                            }
                            supportedAttributes "members"
                        }
                    }
                }
                """;

        initConnector(SCHEMA, CONNID_SCHEMA_SCRIPT, script).updateDelta(new ObjectClass("Account"), new Uid("123"),
                Set.of(AttributeDeltaBuilder.build("members", null, List.of("user-9@example.com"))),
                new OperationOptionsBuilder().build());

        var requests = wireMockServer.findAll(postRequestedFor(urlEqualTo("/accounts/123/members")));
        assertEquals(requests.size(), 1);
        var sent = requestBodyJson(requests.getFirst());
        assertEquals(sent.get("op").asString(), "remove");
        assertEquals(sent.get("email").asString(), "user-9@example.com");
        assertEquals(sent.get("role").asString(), "MEMBER");
    }

    /**
     * A custom-body POST endpoint for {@code members} coexists with a default-serialization PUT
     * endpoint for {@code status} on the same update operation - each endpoint's
     * {@code supportedAttributes} still routes only its own delta to it, regardless of whether
     * the endpoint builds its body itself or leaves it to the framework.
     */
    @Test
    public void customBodyEndpointCoexistsWithDefaultSerializationEndpoint() {
        stubReadById("old@example.com", "active");
        wireMockServer.stubFor(put(urlEqualTo(ACCOUNT_BY_ID_PATH))
                .willReturn(aResponse().withStatus(200)));
        wireMockServer.stubFor(post(urlEqualTo("/accounts/123/members"))
                .willReturn(aResponse().withStatus(201)));

        var script = """
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
                            supportedAttributes "status"
                        }
                        endpoint(POST, "accounts/{id}/members") {
                            request {
                                contentType APPLICATION_JSON
                                body { req ->
                                    def delta = req.attributeDeltaSet().find { it.name == "members" }
                                    def email = delta?.valuesToAdd?.first()
                                    '''
                                    {
                                      "email": "%s",
                                      "role": "MEMBER"
                                    }
                                    '''.formatted(email).getBytes("UTF-8")
                                }
                            }
                            supportedAttributes "members"
                        }
                    }
                }
                """;

        var connector = initConnector(SCHEMA, CONNID_SCHEMA_SCRIPT, script);
        connector.updateDelta(new ObjectClass("Account"), new Uid("123"),
                Set.of(
                        AttributeDeltaBuilder.build("status", List.of("suspended")),
                        AttributeDeltaBuilder.build("members", List.of("user-1@example.com"), null)),
                new OperationOptionsBuilder().build());

        assertEquals(wireMockServer.findAll(putRequestedFor(urlEqualTo(ACCOUNT_BY_ID_PATH))
                .withRequestBody(matchingJsonPath("$.status", equalTo("suspended")))).size(), 1);
        var memberRequests = wireMockServer.findAll(postRequestedFor(urlEqualTo("/accounts/123/members")));
        assertEquals(memberRequests.size(), 1);
        var sent = requestBodyJson(memberRequests.getFirst());
        assertEquals(sent.get("email").asString(), "user-1@example.com");
        assertEquals(sent.get("role").asString(), "MEMBER");
    }
}
