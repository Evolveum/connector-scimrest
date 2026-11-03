/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 */
package com.evolveum.polygon.scimrest.crud;

import com.evolveum.polygon.conndev.spi.ClassHandlerConnectorBase;
import com.evolveum.polygon.scimrest.support.AbstractCrudConnectorTest;
import com.evolveum.polygon.scimrest.support.YamlOperationsConnector;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.exceptions.ConnectionFailedException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.InvalidCredentialException;
import org.identityconnectors.framework.common.exceptions.PermissionDeniedException;
import org.identityconnectors.framework.common.exceptions.RetryableException;
import org.identityconnectors.framework.common.objects.AttributeDelta;
import org.identityconnectors.framework.common.objects.AttributeDeltaBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObjectReference;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.Uid;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public class RelationshipRecordsTest extends AbstractCrudConnectorTest {

    private static final String SCHEMA = """
            objectClass("Account") {
                attribute("id") { jsonType "string" }
                attribute("name") { jsonType "string" }
                attribute("displayName") { jsonType "string" }
                attribute("groups") { jsonType "string"; multiValued true }
            }
            """;

    private static final String OPERATIONS = """
            objectClasses:
              Account:
                update:
                  relationshipRecords:
                    groups:
                      endpoint: /memberships
                      sourceField: userId
                      targetField: groupId
                      idField: id
            """;

    private static final String RECORDS = """
            [{"id":"M1","userId":"123","groupId":"A"},
             {"id":"M2","userId":"123","groupId":"B"},
             {"id":"M3","userId":"other","groupId":"A"}]
            """;

    private static final String GROOVY_OPERATIONS = """
            objectClass("Account") {
                update {
                    relationshipRecords("groups") {
                        endpoint "/memberships"
                        sourceField "userId"
                        targetField "groupId"
                        idField "id"
                    }
                }
            }
            """;

    @Test
    public void testReplaceMemberships() {
        // Given: Existing links and another user
        stubRecords(RECORDS);
        stubDelete("M1");
        stubCreate("{\"userId\":\"123\",\"groupId\":\"C\"}");
        var delta = AttributeDeltaBuilder.build("groups", List.of("B", "C"));

        // When: Replace the requested memberships
        var applied = update(initYaml(OPERATIONS), delta);

        // Then: Keep existing and foreign links
        assertEquals(applied, Set.of(delta));
        assertRequests("GET /memberships", "DELETE /memberships/M1", "POST /memberships");
        verifyCreate("{\"userId\":\"123\",\"groupId\":\"C\"}");
    }

    @Test
    public void testAddMemberships() {
        // Given: Existing links and duplicate input
        stubRecords(RECORDS);
        stubCreate("{\"userId\":\"123\",\"groupId\":\"C\"}");
        var delta = new AttributeDeltaBuilder().setName("groups").addValueToAdd("A", "C", "C").build();

        // When: Add existing and new memberships
        update(initYaml(OPERATIONS), delta);

        // Then: Create only the missing link
        assertRequests("GET /memberships", "POST /memberships");
        verifyCreate("{\"userId\":\"123\",\"groupId\":\"C\"}");
    }

    @Test
    public void testRemoveDuplicates() {
        // Given: Duplicate links and foreign ownership
        stubRecords("""
                [{"id":"M1","userId":"123","groupId":"A"},
                 {"id":"M2","userId":"123","groupId":"A"},
                 {"id":"M3","userId":"other","groupId":"A"}]
                """);
        stubDelete("M1");
        stubDelete("M2");
        var delta = new AttributeDeltaBuilder().setName("groups").addValueToRemove("A", "missing").build();

        // When: Remove duplicated and absent memberships
        update(initYaml(OPERATIONS), delta);

        // Then: Delete both owned relationship records
        assertRequests("GET /memberships", "DELETE /memberships/M1", "DELETE /memberships/M2");
    }

    @Test
    public void testClearMemberships() {
        // Given: Owned and foreign memberships
        stubRecords(RECORDS);
        stubDelete("M1");
        stubDelete("M2");
        var delta = AttributeDeltaBuilder.build("groups", List.of());

        // When: Clear all requested memberships
        update(initYaml(OPERATIONS), delta);

        // Then: Delete only the owned records
        assertRequests("GET /memberships", "DELETE /memberships/M1", "DELETE /memberships/M2");
    }

    @Test
    public void testUnchangedMemberships() {
        // Given: Duplicate existing relationship records
        stubRecords("""
                [{"id":"M1","userId":"123","groupId":"A"},
                 {"id":"M2","userId":"123","groupId":"A"}]
                """);
        var delta = AttributeDeltaBuilder.build("groups", List.of("A", "A"));

        // When: Replace with the same membership
        update(initYaml(OPERATIONS), delta);

        // Then: Preserve all existing record identities
        assertRequests("GET /memberships");
    }

    @Test
    public void testCombinedDelta() {
        // Given: Simultaneous additions and removals
        stubRecords(RECORDS);
        stubDelete("M1");
        stubCreate("{\"userId\":\"123\",\"groupId\":\"C\"}");
        var delta = new AttributeDeltaBuilder().setName("groups").addValueToRemove("A").addValueToAdd("B", "C").build();

        // When: Apply the combined membership delta
        update(initYaml(OPERATIONS), delta);

        // Then: Preserve the unaffected existing membership
        assertRequests("GET /memberships", "DELETE /memberships/M1", "POST /memberships");
        verifyCreate("{\"userId\":\"123\",\"groupId\":\"C\"}");
    }

    @Test
    public void testMixedRouting() {
        // Given: Catchall endpoint before relationship declaration
        var operations = """
                objectClasses:
                  Account:
                    search:
                      endpoints:
                        - path: /accounts/{id}
                          singleResult: true
                          supportedFilters:
                            - spec: attribute("id").eq().anySingleValue()
                              request: request.pathParameter("id", value)
                    update:
                      endpoints:
                        - method: PATCH
                          path: /accounts/{id}
                          request:
                            contentType: APPLICATION_JSON
                      relationshipRecords:
                        groups:
                          endpoint: /memberships
                          sourceField: userId
                          targetField: groupId
                          idField: id
                """;
        stubRecords("[]");
        stubCreate("{\"userId\":\"123\",\"groupId\":\"A\"}");
        wireMockServer.stubFor(get(urlEqualTo("/accounts/123")).willReturn(okJson("{\"id\":\"123\",\"name\":\"account\"}")));
        wireMockServer.stubFor(patch(urlEqualTo("/accounts/123")).withRequestBody(equalToJson("{\"displayName\":\"Renamed\"}")).willReturn(noContent()));
        var membership = AttributeDeltaBuilder.build("groups", List.of("A"));
        var displayName = AttributeDeltaBuilder.build("displayName", List.of("Renamed"));

        // When: Update membership and ordinary attributes
        var applied = update(initYaml(operations), membership, displayName);

        // Then: Route each delta exactly once
        assertEquals(applied, Set.of(membership, displayName));
        assertRequests("GET /accounts/123", "GET /memberships", "POST /memberships", "PATCH /accounts/123");
        wireMockServer.verify(1, patchRequestedFor(urlEqualTo("/accounts/123")).withRequestBody(equalToJson("{\"displayName\":\"Renamed\"}")));
    }

    @Test
    public void testGroovyDeclaration() {
        // Given: Equivalent Groovy relationship configuration
        stubRecords("[]");
        stubCreate("{\"userId\":\"123\",\"groupId\":\"A\"}");

        // When: Add through the Groovy declaration
        update(initConnector(SCHEMA, CONNID_SCHEMA_SCRIPT, GROOVY_OPERATIONS), AttributeDeltaBuilder.build("groups", List.of("A")));

        // Then: Execute native relationship record creation
        assertRequests("GET /memberships", "POST /memberships");
        verifyCreate("{\"userId\":\"123\",\"groupId\":\"A\"}");
    }

    @Test
    public void testReferenceMemberships() {
        // Given: Reference values identify target groups
        var schema = SCHEMA.replace("attribute(\"groups\") { jsonType \"string\"; multiValued true }",
                "reference(\"groups\") { objectClass \"Group\"; multiValued true }")
                + "objectClass(\"Group\") { attribute(\"id\") { jsonType \"string\" } }";
        var connector = YamlOperationsConnector.fromStrings(OPERATIONS).withSchema(schema, CONNID_SCHEMA_SCRIPT);
        connector.init(new SimpleConfig(wireMockServer.port()));
        var reference = new ConnectorObjectReference(new ConnectorObjectBuilder().setObjectClass(new ObjectClass("Group")).setUid("C").buildIdentification());
        stubRecords(RECORDS);
        stubCreate("{\"userId\":\"123\",\"groupId\":\"C\"}");

        // When: Add membership using a ConnId reference
        update(connector, new AttributeDeltaBuilder().setName("groups").addValueToAdd(reference).build());

        // Then: Use the referenced UID in JSON
        assertRequests("GET /memberships", "POST /memberships");
        verifyCreate("{\"userId\":\"123\",\"groupId\":\"C\"}");
    }

    @Test
    public void testAttributeAlias() {
        // Given: Membership attribute has a ConnId alias
        var schema = SCHEMA.replace("multiValued true", "multiValued true; connId { name \"memberships\" }");
        var connector = YamlOperationsConnector.fromStrings(OPERATIONS).withSchema(schema, CONNID_SCHEMA_SCRIPT);
        connector.init(new SimpleConfig(wireMockServer.port()));
        stubRecords("[]");
        stubCreate("{\"userId\":\"123\",\"groupId\":\"A\"}");
        var delta = AttributeDeltaBuilder.build("memberships", List.of("A"));

        // When: Update through the ConnId attribute name
        var applied = update(connector, delta);

        // Then: Resolve the native declaration correctly
        assertEquals(applied, Set.of(delta));
        assertRequests("GET /memberships", "POST /memberships");
    }

    @Test
    public void testNestedIntegerFields() {
        // Given: Wrapped records and nested numeric identifiers
        var operations = OPERATIONS.replace("sourceField: userId", "sourceField: { type: JSON_POINTER, value: /user/id }\n          sourceType: integer")
                .replace("targetField: groupId", "targetField: $.group.id\n          targetType: integer")
                .replace("idField: id", "idField: $.record.id\n          recordsPath: { type: JSON_POINTER, value: /items }");
        stubRecords("{\"items\":[{\"record\":{\"id\":9},\"user\":{\"id\":123},\"group\":{\"id\":4}}]}");
        stubCreate("{\"user\":{\"id\":123},\"group\":{\"id\":5}}");

        // When: Preserve one group and add another
        update(initYaml(operations), AttributeDeltaBuilder.build("groups", List.of("4", "5")));

        // Then: Serialize configured numeric nested fields
        assertRequests("GET /memberships", "POST /memberships");
        verifyCreate("{\"user\":{\"id\":123},\"group\":{\"id\":5}}");
    }

    @Test
    public void testPagedSnapshot() {
        // Given: More records beyond a short page
        var operations = OPERATIONS + "          pageSize: 2\n";
        stubPage(0, "[{\"id\":\"M1\",\"userId\":\"123\",\"groupId\":\"A\"}]");
        stubPage(1, "[{\"id\":\"M2\",\"userId\":\"123\",\"groupId\":\"B\"}]");
        stubPage(2, "[]");
        stubDelete("M1");

        // When: Replace using the complete remote snapshot
        update(initYaml(operations), AttributeDeltaBuilder.build("groups", List.of("B")));

        // Then: Preserve the later existing membership
        assertRequests("GET /memberships?offset=0&limit=2", "GET /memberships?offset=1&limit=2", "GET /memberships?offset=2&limit=2", "DELETE /memberships/M1");
    }

    @Test
    public void testRepeatedPage() {
        // Given: Server ignores the paging offset
        var operations = OPERATIONS + "          pageSize: 2\n";
        var records = "[{\"id\":\"M1\",\"userId\":\"123\",\"groupId\":\"A\"}]";
        stubPage(0, records);
        stubPage(1, records);

        // When: Attempt replacement with repeated pages
        var failure = expectThrows(ConnectorException.class, () -> update(initYaml(operations), AttributeDeltaBuilder.build("groups", List.of())));

        // Then: Abort before deleting any membership
        assertTrue(failure.getMessage().contains("repeated membership-record ID"), failure.getMessage());
        assertRequests("GET /memberships?offset=0&limit=2", "GET /memberships?offset=1&limit=2");
    }

    @Test
    public void testPageLimit() {
        // Given: Snapshot exceeds the configured page limit
        var operations = OPERATIONS + "          pageSize: 2\n          maxPages: 1\n";
        stubPage(0, "[{\"id\":\"M1\",\"userId\":\"123\",\"groupId\":\"A\"}]");

        // When: Attempt clear without a complete snapshot
        expectThrows(ConnectorException.class, () -> update(initYaml(operations), AttributeDeltaBuilder.build("groups", List.of())));

        // Then: Perform no writes from partial results
        assertRequests("GET /memberships?offset=0&limit=2");
    }

    @DataProvider
    public Object[][] malformedRecords() {
        return new Object[][] {
                {"{}"}, {"null"}, {"[null]"}, {"[\"invalid\"]"}, {"not-json"},
                {"[{\"id\":\"M1\",\"groupId\":\"A\"}]"},
                {"[{\"userId\":\"123\",\"groupId\":\"A\"}]"},
                {"[{\"id\":\"M1\",\"userId\":\"123\"}]"},
                {"[{\"id\":\"M1\",\"userId\":\"123\",\"groupId\":{\"id\":\"A\"}}]"},
                {"[{\"id\":\"foreign\",\"userId\":{},\"groupId\":\"A\"}]"},
                {"[{\"id\":\"M1\",\"userId\":\"123\",\"groupId\":\"A\"},{\"id\":\"M1\",\"userId\":\"other\",\"groupId\":\"B\"}]"}
        };
    }

    @Test(dataProvider = "malformedRecords")
    public void testMalformedSnapshot(String records) {
        // Given: Malformed or ambiguous relationship records
        stubRecords(records);

        // When: Attempt clearing the affected memberships
        expectThrows(ConnectorException.class, () -> update(initYaml(OPERATIONS), AttributeDeltaBuilder.build("groups", List.of())));

        // Then: Reject the snapshot before any mutation
        assertRequests("GET /memberships");
    }

    @Test
    public void testLaterMalformedRecord() {
        // Given: Valid obsolete record precedes invalid data
        stubRecords("[{\"id\":\"M1\",\"userId\":\"123\",\"groupId\":\"A\"},{\"id\":\"M2\",\"userId\":\"123\"}]");

        // When: Attempt clearing the entire snapshot
        expectThrows(ConnectorException.class, () -> update(initYaml(OPERATIONS), AttributeDeltaBuilder.build("groups", List.of())));

        // Then: Validate everything before the first delete
        assertRequests("GET /memberships");
    }

    @DataProvider
    public Object[][] searchFailures() {
        return new Object[][] {
                {401, InvalidCredentialException.class}, {403, PermissionDeniedException.class}, {404, ConfigurationException.class},
                {429, RetryableException.class}, {503, ConnectionFailedException.class}
        };
    }

    @Test(dataProvider = "searchFailures")
    public void testSearchFailure(int status, Class<? extends Throwable> failureType) {
        // Given: Relationship discovery fails remotely
        wireMockServer.stubFor(get(urlEqualTo("/memberships")).willReturn(aResponse().withStatus(status).withBody("unavailable")));

        // When: Attempt a relationship replacement
        var failure = expectThrows(failureType, () -> update(initYaml(OPERATIONS), AttributeDeltaBuilder.build("groups", List.of("A"))));

        // Then: Preserve failure type without remote writes
        assertTrue(failure.getMessage().contains(String.valueOf(status)), failure.getMessage());
        assertRequests("GET /memberships");
    }

    @Test
    public void testDeleteFailure() {
        // Given: Obsolete membership cannot be deleted
        stubRecords("[{\"id\":\"M1\",\"userId\":\"123\",\"groupId\":\"A\"}]");
        wireMockServer.stubFor(delete(urlEqualTo("/memberships/M1")).willReturn(aResponse().withStatus(403)));

        // When: Replace the membership with another
        expectThrows(PermissionDeniedException.class, () -> update(initYaml(OPERATIONS), AttributeDeltaBuilder.build("groups", List.of("B"))));

        // Then: Stop before creating the replacement
        assertRequests("GET /memberships", "DELETE /memberships/M1");
    }

    @Test
    public void testCreateFailure() {
        // Given: Remote uniqueness rejects record creation
        stubRecords("[]");
        wireMockServer.stubFor(post(urlEqualTo("/memberships")).withRequestBody(equalToJson("{\"userId\":\"123\",\"groupId\":\"A\"}"))
                .willReturn(aResponse().withStatus(409)));

        // When: Attempt adding the conflicting membership
        expectThrows(AlreadyExistsException.class, () -> update(initYaml(OPERATIONS), AttributeDeltaBuilder.build("groups", List.of("A"))));

        // Then: Surface the conflict without silent success
        assertRequests("GET /memberships", "POST /memberships");
    }

    @DataProvider
    public Object[][] incompleteStatuses() {
        return new Object[][] {{202}, {206}, {207}};
    }

    @Test(dataProvider = "incompleteStatuses")
    public void testUnconfirmedCreate(int status) {
        // Given: Creation receives an incomplete success status
        stubRecords("[]");
        wireMockServer.stubFor(post(urlEqualTo("/memberships")).withRequestBody(equalToJson("{\"userId\":\"123\",\"groupId\":\"A\"}"))
                .willReturn(aResponse().withStatus(status)));

        // When: Attempt adding the requested membership
        var failure = expectThrows(ConnectorException.class, () -> update(initYaml(OPERATIONS), AttributeDeltaBuilder.build("groups", List.of("A"))));

        // Then: Do not report unconfirmed changes applied
        assertTrue(failure.getMessage().contains(String.valueOf(status)), failure.getMessage());
        assertRequests("GET /memberships", "POST /memberships");
    }

    @Test(dataProvider = "incompleteStatuses")
    public void testUnconfirmedDelete(int status) {
        // Given: Deletion receives an incomplete success status
        stubRecords("[{\"id\":\"M1\",\"userId\":\"123\",\"groupId\":\"A\"}]");
        wireMockServer.stubFor(delete(urlEqualTo("/memberships/M1")).willReturn(aResponse().withStatus(status)));

        // When: Replace with a different membership
        var failure = expectThrows(ConnectorException.class, () -> update(initYaml(OPERATIONS), AttributeDeltaBuilder.build("groups", List.of("B"))));

        // Then: Stop before attempting the replacement create
        assertTrue(failure.getMessage().contains(String.valueOf(status)), failure.getMessage());
        assertRequests("GET /memberships", "DELETE /memberships/M1");
    }

    @Test
    public void testMissingDelete() {
        // Given: Membership disappears after snapshot discovery
        stubRecords("[{\"id\":\"M1\",\"userId\":\"123\",\"groupId\":\"A\"}]");
        wireMockServer.stubFor(delete(urlEqualTo("/memberships/M1")).willReturn(notFound()));
        var delta = AttributeDeltaBuilder.build("groups", List.of());

        // When: Clear the already removed membership
        var applied = update(initYaml(OPERATIONS), delta);

        // Then: Confirm the requested absent membership
        assertEquals(applied, Set.of(delta));
        assertRequests("GET /memberships", "DELETE /memberships/M1");
    }

    @Test
    public void testPartialRetry() {
        // Given: Creation fails after successful record deletion
        var connector = initYaml(OPERATIONS);
        var delta = AttributeDeltaBuilder.build("groups", List.of("B"));
        wireMockServer.stubFor(get(urlEqualTo("/memberships")).inScenario("retry").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(okJson("[{\"id\":\"M1\",\"userId\":\"123\",\"groupId\":\"A\"}]")));
        wireMockServer.stubFor(delete(urlEqualTo("/memberships/M1")).inScenario("retry").whenScenarioStateIs(Scenario.STARTED)
                .willSetStateTo("deleted").willReturn(noContent()));
        wireMockServer.stubFor(post(urlEqualTo("/memberships")).inScenario("retry").whenScenarioStateIs("deleted")
                .withRequestBody(equalToJson("{\"userId\":\"123\",\"groupId\":\"B\"}")).willSetStateTo("retrying").willReturn(aResponse().withStatus(503)));
        wireMockServer.stubFor(get(urlEqualTo("/memberships")).inScenario("retry").whenScenarioStateIs("retrying").willReturn(okJson("[]")));
        wireMockServer.stubFor(post(urlEqualTo("/memberships")).inScenario("retry").whenScenarioStateIs("retrying")
                .withRequestBody(equalToJson("{\"userId\":\"123\",\"groupId\":\"B\"}")).willSetStateTo("created").willReturn(aResponse().withStatus(201)));

        // When: Retry the partially failed replacement
        var failure = expectThrows(ConnectionFailedException.class, () -> update(connector, delta));
        var applied = update(connector, delta);

        // Then: Reconcile remaining work without repeated deletion
        assertTrue(failure.getMessage().contains("503"), failure.getMessage());
        assertEquals(applied, Set.of(delta));
        assertRequests("GET /memberships", "DELETE /memberships/M1", "POST /memberships", "GET /memberships", "POST /memberships");
        assertEquals(wireMockServer.getAllScenarios().getScenarios().getFirst().getState(), "created");
    }

    @Test
    public void testSourceFilter() {
        // Given: Endpoint supports a source query filter
        var operations = OPERATIONS + "          sourceParameter: user\n";
        wireMockServer.stubFor(get(urlEqualTo("/memberships?user=123")).willReturn(okJson(RECORDS)));
        stubDelete("M1");
        stubDelete("M2");

        // When: Clear memberships through filtered discovery
        update(initYaml(operations), AttributeDeltaBuilder.build("groups", List.of()));

        // Then: Still independently protect foreign memberships
        assertRequests("GET /memberships?user=123", "DELETE /memberships/M1", "DELETE /memberships/M2");
    }

    @Test
    public void testDeletePath() {
        // Given: Record identifier contains a path separator
        var operations = OPERATIONS + "          deleteEndpoint: /links/{id}\n";
        stubRecords("[{\"id\":\"M/1\",\"userId\":\"123\",\"groupId\":\"A\"}]");
        wireMockServer.stubFor(delete(urlEqualTo("/links/M%2F1")).willReturn(noContent()));

        // When: Remove the record using its identifier
        update(initYaml(operations), AttributeDeltaBuilder.build("groups", List.of()));

        // Then: Encode the identifier as one segment
        assertRequests("GET /memberships", "DELETE /links/M%2F1");
    }

    @DataProvider
    public Object[][] invalidIdentifiers() {
        return new Object[][] {{" "}, {true}, {1.5d}};
    }

    @Test(dataProvider = "invalidIdentifiers")
    public void testInvalidIdentifier(Object identifier) {
        // Given: A malformed target identifier
        var connector = initYaml(OPERATIONS);
        var delta = AttributeDeltaBuilder.build("groups", List.of(identifier));

        // When: Attempt replacing the membership
        expectThrows(ConnectorException.class, () -> update(connector, delta));

        // Then: Reject input before any request
        assertRequests();
    }

    @Test
    public void testConcurrentAdds() throws Exception {
        // Given: Concurrent additions through one connector
        var connector = initStable();
        stubRecords("[]");
        update(connector, AttributeDeltaBuilder.build("groups", List.of()));
        wireMockServer.resetAll();
        wireMockServer.stubFor(get(urlEqualTo("/memberships")).inScenario("membership").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(okJson("[]").withFixedDelay(150)));
        wireMockServer.stubFor(post(urlEqualTo("/memberships")).inScenario("membership").whenScenarioStateIs(Scenario.STARTED)
                .withRequestBody(equalToJson("{\"userId\":\"123\",\"groupId\":\"A\"}")).willSetStateTo("created").willReturn(aResponse().withStatus(201)));
        wireMockServer.stubFor(get(urlEqualTo("/memberships")).inScenario("membership").whenScenarioStateIs("created")
                .willReturn(okJson("[{\"id\":\"M1\",\"userId\":\"123\",\"groupId\":\"A\"}]")));
        var delta = new AttributeDeltaBuilder().setName("groups").addValueToAdd("A").build();
        var start = new CountDownLatch(1);

        // When: Add the same membership simultaneously
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                return update(connector, delta);
            });
            var second = executor.submit(() -> {
                start.await();
                return update(connector, delta);
            });
            start.countDown();

            // Then: Both succeed with one record creation
            assertEquals(first.get(10, TimeUnit.SECONDS), Set.of(delta));
            assertEquals(second.get(10, TimeUnit.SECONDS), Set.of(delta));
        }
        assertRequests("GET /memberships", "POST /memberships", "GET /memberships");
    }

    private ClassHandlerConnectorBase initYaml(String operations) {
        var connector = YamlOperationsConnector.fromStrings(operations).withSchema(SCHEMA, CONNID_SCHEMA_SCRIPT);
        connector.init(new SimpleConfig(wireMockServer.port()));
        return connector;
    }

    private ClassHandlerConnectorBase initStable() {
        var connector = new TestConnector(SCHEMA, CONNID_SCHEMA_SCRIPT, GROOVY_OPERATIONS) {
            @Override
            protected boolean shouldReinitialize() {
                return false;
            }
        };
        connector.init(new SimpleConfig(wireMockServer.port()));
        return connector;
    }

    private Set<AttributeDelta> update(ClassHandlerConnectorBase connector, AttributeDelta... deltas) {
        return connector.updateDelta(new ObjectClass("Account"), new Uid("123"), Set.of(deltas), new OperationOptionsBuilder().build());
    }

    private void stubRecords(String records) {
        wireMockServer.stubFor(get(urlEqualTo("/memberships")).willReturn(okJson(records)));
    }

    private void stubPage(int offset, String records) {
        wireMockServer.stubFor(get(urlPathEqualTo("/memberships")).withQueryParam("offset", equalTo(String.valueOf(offset)))
                .withQueryParam("limit", equalTo("2")).willReturn(okJson(records)));
    }

    private void stubDelete(String identifier) {
        wireMockServer.stubFor(delete(urlEqualTo("/memberships/" + identifier)).willReturn(noContent()));
    }

    private void stubCreate(String body) {
        wireMockServer.stubFor(post(urlEqualTo("/memberships")).withRequestBody(equalToJson(body)).willReturn(aResponse().withStatus(201)));
    }

    private void verifyCreate(String body) {
        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/memberships")).withHeader("Content-Type", equalTo("application/json")).withRequestBody(equalToJson(body)));
    }

    private void assertRequests(String... expected) {
        var actual = wireMockServer.getAllServeEvents().stream()
                .map(event -> normalizeRequest(event.getRequest().getMethod() + " " + event.getRequest().getUrl())).sorted().toList();
        assertEquals(actual, Arrays.stream(expected).map(RelationshipRecordsTest::normalizeRequest).sorted().toList(), "Actual requests: " + actual);
    }

    private static String normalizeRequest(String request) {
        var parts = request.split("\\?", 2);
        return parts.length == 1 ? request : parts[0] + "?" + String.join("&", Arrays.stream(parts[1].split("&")).sorted().toList());
    }
}
