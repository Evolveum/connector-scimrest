/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 */
package com.evolveum.polygon.scimrest.crud;

import com.evolveum.polygon.conndev.spi.ClassHandlerConnectorBase;
import com.evolveum.polygon.scimrest.support.AbstractCrudConnectorTest;
import com.evolveum.polygon.scimrest.support.YamlOperationsConnector;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectReference;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import tools.jackson.databind.node.JsonNodeFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.*;

/**
Verifies relationship retrieval through public connector searches and real paginated HTTP responses.
*/
public class RelationshipReadTest extends AbstractCrudConnectorTest {

    static final String SCHEMA = """
            objectClass("Account") {
                attribute("id") { jsonType "string" }
                attribute("name") { jsonType "string" }
                reference("groups") { objectClass "Group"; multiValued true }
            }
            objectClass("Group") { attribute("id") { jsonType "string" } }
            """;

    static final String PAGING = """
            pagingSupport:
              pageSize: 100
              parameters:
                offset: {in: query, name: offset}
                pageSize: {in: query, name: limit}
            """.indent(10);

    static final String OPERATIONS = """
            objectClasses:
              Account:
                search:
                  endpoints:
                    - path: accounts
                      responseFormat: JSON_ARRAY
                      emptyFilterSupported: true
                  relationshipRecords:
                    groups:
                      endpoint: /memberships
                      sourceParameter: user
                      objectsPath: {type: JSON_POINTER, value: /result}
                      targetField: {type: JSON_POINTER, value: /group/id}
            """ + PAGING;

    @Test
    public void testCompleteMemberships() {
        // Given: Memberships span two populated pages
        stubAccounts("123");
        stubPage("123", 0, 100, records(1, 100));
        stubPage("123", 100, 100, records(101, 150));
        stubPage("123", 150, 100, "[]");

        // When: Search through declarative YAML configuration
        var results = search(initYaml(OPERATIONS), null);

        // Then: Report every typed group reference
        assertEquals(results.size(), 1);
        assertEquals(results.getFirst().getUid(), new Uid("123"));
        assertGroups(results.getFirst(), IntStream.rangeClosed(1, 150).mapToObj(number -> "G" + number).toList());
        assertRequests("GET /accounts", pageRequest("123", 0, 100), pageRequest("123", 100, 100), pageRequest("123", 150, 100));
    }

    @Test
    public void testCappedPages() {
        // Given: Server returns fewer records than requested
        stubAccounts("123");
        stubPage("123", 0, 100, records(1, 2));
        stubPage("123", 2, 100, records(3, 3));
        stubPage("123", 3, 100, "[]");

        // When: Resolve memberships across short pages
        var results = search(initYaml(OPERATIONS), null);

        // Then: Continue by actual returned record count
        assertGroups(results.getFirst(), List.of("G1", "G2", "G3"));
        assertRequests("GET /accounts", pageRequest("123", 0, 100), pageRequest("123", 2, 100), pageRequest("123", 3, 100));
    }

    @Test
    public void testGroovyLookup() {
        // Given: Groovy declarations use custom paging parameters
        var operations = """
                objectClass("Account") {
                    search {
                        endpoint("accounts/{id}") {
                            singleResult()
                            supportedFilter(attribute("id").eq().anySingleValue()) { request.pathParameter("id", value) }
                        }
                        relationshipRecords("groups") {
                            endpoint "/memberships"
                            sourceParameter "user"
                            objectsPath '$.result'
                            targetField '$.group.id'
                            pageSize 2
                            pagingParameter 'offset', 'query', 'start'
                            pagingParameter 'pageSize', 'query', 'count'
                            initialOffset 1
                        }
                    }
                }
                """;
        wireMockServer.stubFor(get(urlEqualTo("/accounts/123")).willReturn(okJson("{\"id\":\"123\",\"name\":\"Alice\"}")));
        wireMockServer.stubFor(get(urlPathEqualTo("/memberships")).withQueryParam("user", equalTo("123"))
                .withQueryParam("start", equalTo("1")).withQueryParam("count", equalTo("2")).willReturn(okJson("{\"result\":" + records(1, 2) + "}")));
        wireMockServer.stubFor(get(urlPathEqualTo("/memberships")).withQueryParam("user", equalTo("123"))
                .withQueryParam("start", equalTo("3")).withQueryParam("count", equalTo("2")).willReturn(okJson("{\"result\":[]}")));

        // When: Retrieve one user by UID
        var results = search(initConnector(SCHEMA, CONNID_SCHEMA_SCRIPT, operations), FilterBuilder.equalTo(new Uid("123")));

        // Then: Resolve typed references with configured offsets
        assertEquals(results.size(), 1);
        assertGroups(results.getFirst(), List.of("G1", "G2"));
        assertRequests("GET /accounts/123", "GET /memberships?user=123&start=1&count=2", "GET /memberships?user=123&start=3&count=2");
    }

    @Test
    public void testPagingHeaders() {
        // Given: Membership pagination uses named HTTP headers
        var operations = OPERATIONS.replace("offset: {in: query, name: offset}", "offset: {in: header, name: X-Offset}")
                .replace("pageSize: {in: query, name: limit}", "pageSize: {in: header, name: X-Limit}");
        stubAccounts("123");
        wireMockServer.stubFor(get(urlPathEqualTo("/memberships")).withQueryParam("user", equalTo("123"))
                .withHeader("X-Offset", equalTo("0")).withHeader("X-Limit", equalTo("100")).willReturn(okJson("{\"result\":" + records(1, 2) + "}")));
        wireMockServer.stubFor(get(urlPathEqualTo("/memberships")).withQueryParam("user", equalTo("123"))
                .withHeader("X-Offset", equalTo("2")).withHeader("X-Limit", equalTo("100")).willReturn(okJson("{\"result\":" + records(3, 3) + "}")));
        wireMockServer.stubFor(get(urlPathEqualTo("/memberships")).withQueryParam("user", equalTo("123"))
                .withHeader("X-Offset", equalTo("3")).withHeader("X-Limit", equalTo("100")).willReturn(okJson("{\"result\":[]}")));

        // When: Resolve memberships through declarative header paging
        var results = search(initYaml(operations), null);

        // Then: Advance by actual records without accumulation
        assertGroups(results.getFirst(), List.of("G1", "G2", "G3"));
        assertRequests("GET /accounts", "GET /memberships?user=123", "GET /memberships?user=123", "GET /memberships?user=123");
        var requests = wireMockServer.getAllServeEvents().stream().map(event -> event.getRequest())
                .filter(request -> request.getUrl().startsWith("/memberships")).toList();
        assertEquals(requests.stream().map(request -> request.header("X-Offset").values()).sorted(java.util.Comparator.comparing(List::getFirst)).toList(),
                List.of(List.of("0"), List.of("2"), List.of("3")));
        for (var request : requests) {
            assertEquals(request.header("X-Limit").values(), List.of("100"));
            assertEquals(request.getQueryParams().keySet(), java.util.Set.of("user"));
        }
    }

    @Test
    public void testMixedPaging() {
        // Given: Header and query reuse one name
        var operations = OPERATIONS.replace("offset: {in: query, name: offset}", "offset: {in: header, name: user}");
        stubAccounts("123");
        wireMockServer.stubFor(get(urlPathEqualTo("/memberships")).withQueryParam("user", equalTo("123"))
                .withQueryParam("limit", equalTo("100")).withHeader("user", equalTo("0")).willReturn(okJson("{\"result\":[]}")));

        // When: Resolve membership with separate parameter namespaces
        var results = search(initYaml(operations), null);

        // Then: Preserve both values without a collision
        assertGroups(results.getFirst(), List.of());
        assertRequests("GET /accounts", "GET /memberships?user=123&limit=100");
        var request = wireMockServer.getAllServeEvents().stream().map(event -> event.getRequest())
                .filter(candidate -> candidate.getUrl().startsWith("/memberships")).findFirst().orElseThrow();
        assertEquals(request.header("user").values(), List.of("0"));
        assertEquals(request.queryParameter("user").values(), List.of("123"));
    }

    @Test
    public void testSourceTemplate() {
        // Given: UID contains query delimiters and spaces
        var source = "A/B +&=ž";
        var operations = OPERATIONS.replace("sourceParameter: user", "sourceParameter: filter\n          sourceTemplate: 'user={uid}'");
        stubAccounts(source);
        wireMockServer.stubFor(get(urlPathEqualTo("/memberships")).withQueryParam("filter", equalTo("user=" + source))
                .withQueryParam("offset", equalTo("0")).withQueryParam("limit", equalTo("100")).willReturn(okJson("{\"result\":[]}")));

        // When: Resolve a templated membership query
        var results = search(initYaml(operations), null);

        // Then: Preserve the UID as one parameter
        assertEquals(results.getFirst().getUid(), new Uid(source));
        assertGroups(results.getFirst(), List.of());
        assertEquals(wireMockServer.getAllServeEvents().size(), 2);
        var request = wireMockServer.getAllServeEvents().stream().map(event -> event.getRequest())
                .filter(candidate -> candidate.getUrl().startsWith("/memberships")).findFirst().orElseThrow();
        assertEquals(request.getQueryParams().keySet(), java.util.Set.of("filter", "offset", "limit"));
        assertEquals(request.queryParameter("filter").values(), List.of("user=" + source));
    }

    @Test
    public void testJsonTemplate() {
        // Given: Membership filter embeds UID in JSON
        var template = "[{\"user\":{\"operator\":\"=\",\"values\":[\"{uid}\"]}}]";
        var expected = "[{\"user\":{\"operator\":\"=\",\"values\":[\"123\"]}}]";
        var operations = OPERATIONS.replace("sourceParameter: user", "sourceParameter: filter\n          sourceTemplate: '" + template + "'");
        stubAccounts("123");
        wireMockServer.stubFor(get(urlPathEqualTo("/memberships")).withQueryParam("filter", equalTo(expected))
                .withQueryParam("offset", equalTo("0")).withQueryParam("limit", equalTo("100")).willReturn(okJson("{\"result\":[]}")));

        // When: Resolve declaratively templated JSON membership filter
        var results = search(initYaml(operations), null);

        // Then: Preserve JSON braces and substitute UID
        assertGroups(results.getFirst(), List.of());
        assertEquals(wireMockServer.getAllServeEvents().size(), 2);
        var request = wireMockServer.getAllServeEvents().stream().map(event -> event.getRequest())
                .filter(candidate -> candidate.getUrl().startsWith("/memberships")).findFirst().orElseThrow();
        assertEquals(request.getQueryParams().keySet(), java.util.Set.of("filter", "offset", "limit"));
        assertEquals(request.queryParameter("filter").values(), List.of(expected));
    }

    @Test
    public void testSourcePath() {
        // Given: Endpoint scopes membership through encoded UID
        var operations = OPERATIONS.replace("endpoint: /memberships", "endpoint: /users/{source}/memberships").replace("          sourceParameter: user\n", "");
        stubAccounts("A/B C");
        wireMockServer.stubFor(get(urlEqualTo("/users/A%2FB%20C/memberships?offset=0&limit=100")).willReturn(okJson("{\"result\":[]}")));

        // When: Resolve memberships through source path
        var results = search(initYaml(operations), null);

        // Then: Encode source as one path segment
        assertGroups(results.getFirst(), List.of());
        assertRequests("GET /accounts", "GET /users/A%2FB%20C/memberships?offset=0&limit=100");
    }

    @Test
    public void testDuplicateTargets() {
        // Given: Records repeat targets within separate pages
        stubAccounts("123");
        stubPage("123", 0, 100, "[{\"group\":{\"id\":42}},{\"group\":{\"id\":\"G2\"}}]");
        stubPage("123", 2, 100, "[{\"group\":{\"id\":\"G2\"}},{\"group\":{\"id\":42}},{\"group\":{\"id\":\"G3\"}}]");
        stubPage("123", 5, 100, "[]");

        // When: Read overlapping membership pages
        var results = search(initYaml(OPERATIONS), null);

        // Then: Deduplicate references without changing raw offsets
        assertGroups(results.getFirst(), List.of("42", "G2", "G3"));
        assertRequests("GET /accounts", pageRequest("123", 0, 100), pageRequest("123", 2, 100), pageRequest("123", 5, 100));
    }

    @Test
    public void testSourceOwnership() {
        // Given: Response includes a foreign user's membership
        var operations = OPERATIONS + "          sourceField: userId\n";
        stubAccounts("123");
        stubPage("123", 0, 100, "[{\"userId\":\"other\",\"group\":{\"id\":\"G9\"}},{\"userId\":123,\"group\":{\"id\":\"G1\"}}]");
        stubPage("123", 2, 100, "[]");

        // When: Resolve only source-owned records
        var results = search(initYaml(operations), null);

        // Then: Exclude foreign groups while advancing offsets
        assertGroups(results.getFirst(), List.of("G1"));
        assertRequests("GET /accounts", pageRequest("123", 0, 100), pageRequest("123", 2, 100));
    }

    @Test
    public void testIndependentUsers() {
        // Given: Each user has distinct membership pages
        stubAccounts("123", "456");
        stubPage("123", 0, 100, records(1, 1));
        stubPage("123", 1, 100, "[]");
        stubPage("456", 0, 100, records(2, 3));
        stubPage("456", 2, 100, "[]");

        // When: Search several users together
        var results = search(initYaml(OPERATIONS), null);

        // Then: Keep identifiers and paging per user
        assertEquals(results.stream().map(account -> account.getUid().getUidValue()).toList(), List.of("123", "456"));
        assertGroups(results.get(0), List.of("G1"));
        assertGroups(results.get(1), List.of("G2", "G3"));
        assertRequests("GET /accounts", pageRequest("123", 0, 100), pageRequest("123", 1, 100), pageRequest("456", 0, 100), pageRequest("456", 2, 100));
    }

    @Test
    public void testOuterPaging() {
        // Given: Caller requests only one user
        stubAccounts("123");
        stubPage("123", 0, 2, records(1, 2));
        stubPage("123", 2, 2, records(3, 3));
        stubPage("123", 3, 2, "[]");
        var options = new OperationOptionsBuilder().setPageSize(1).setPagedResultsOffset(1).build();

        // When: Read a window of user results
        var results = search(initYaml(OPERATIONS.replace("pageSize: 100", "pageSize: 2")), null, options);

        // Then: Still resolve the user's complete memberships
        assertEquals(results.size(), 1);
        assertGroups(results.getFirst(), List.of("G1", "G2", "G3"));
        assertRequests("GET /accounts", pageRequest("123", 0, 2), pageRequest("123", 2, 2), pageRequest("123", 3, 2));
    }

    @Test
    public void testEmptyCollection() {
        // Given: Source has no membership records
        stubAccounts("123");
        stubPage("123", 0, 100, "[]");

        // When: Resolve an empty relationship collection
        var results = search(initYaml(OPERATIONS), null);

        // Then: Publish an explicitly empty reference attribute
        assertEquals(results.size(), 1);
        assertGroups(results.getFirst(), List.of());
        assertRequests("GET /accounts", pageRequest("123", 0, 100));
    }

    @Test
    public void testRootCollection() {
        // Given: Endpoint returns an unwrapped collection
        var operations = OPERATIONS.replace("          objectsPath: {type: JSON_POINTER, value: /result}\n", "");
        stubAccounts("123");
        wireMockServer.stubFor(membership(0).willReturn(okJson(records(1, 1))));
        wireMockServer.stubFor(membership(1).willReturn(okJson("[]")));

        // When: Read records from response root
        var results = search(initYaml(operations), null);

        // Then: Resolve references without a wrapper path
        assertGroups(results.getFirst(), List.of("G1"));
        assertRequests("GET /accounts", pageRequest("123", 0, 100), pageRequest("123", 1, 100));
    }

    @Test
    public void testColonEndpoint() {
        // Given: Relative collection endpoint contains a colon
        stubAccounts("123");
        var operations = OPERATIONS.replace("endpoint: /memberships", "endpoint: /memberships:search");
        wireMockServer.stubFor(get(urlPathEqualTo("/memberships:search")).withQueryParam("user", equalTo("123"))
                .withQueryParam("offset", equalTo("0")).withQueryParam("limit", equalTo("100")).willReturn(okJson("{\"result\":[]}")));

        // When: Resolve a valid relative action endpoint
        var results = search(initYaml(operations), null);

        // Then: Treat the colon as path content
        assertGroups(results.getFirst(), List.of());
        assertRequests("GET /accounts", "GET /memberships:search?user=123&offset=0&limit=100");
    }

    @Test
    public void testSkipResolution() {
        // Given: Caller explicitly disables attribute resolution
        stubAccounts("123");
        var options = new OperationOptionsBuilder().setOption("skipAttributeResolution", true).build();

        // When: Search without resolving additional attributes
        var results = search(initYaml(OPERATIONS), null, options);

        // Then: Avoid all membership endpoint requests
        assertEquals(results.size(), 1);
        assertNull(results.getFirst().getAttributeByName("groups"));
        assertRequests("GET /accounts");
    }

    @Test
    public void testExistingAttribute() {
        // Given: Native response already contains the attribute
        wireMockServer.stubFor(get(urlEqualTo("/accounts")).willReturn(okJson("[{\"id\":\"123\",\"name\":\"Alice\",\"groups\":[]}]")));
        var schema = SCHEMA.replace("objectClass \"Group\"; multiValued true",
                "objectClass 'Group'; multiValued true; json { path attribute('groups'); referenceBy { uidFrom 'id' } }");
        var connector = YamlOperationsConnector.fromStrings(OPERATIONS).withSchema(schema, CONNID_SCHEMA_SCRIPT);
        connector.init(new SimpleConfig(wireMockServer.port()));

        // When: Search using the normal resolver lifecycle
        var results = search(connector, null);

        // Then: Preserve existing values without extra requests
        assertEquals(results.size(), 1);
        assertGroups(results.getFirst(), List.of());
        assertRequests("GET /accounts");
    }

    @Test
    public void testNullOptions() {
        // Given: Caller supplies no operation options
        stubAccounts("123");
        stubPage("123", 0, 100, records(1, 1));
        stubPage("123", 1, 100, "[]");
        var connector = initYaml(OPERATIONS);
        var results = new ArrayList<ConnectorObject>();

        // When: Execute the public search with null
        connector.executeQuery(new ObjectClass("Account"), null, account -> { results.add(account); return true; }, null);

        // Then: Apply default complete attribute resolution
        assertEquals(results.size(), 1);
        assertGroups(results.getFirst(), List.of("G1"));
        assertRequests("GET /accounts", pageRequest("123", 0, 100), pageRequest("123", 1, 100));
    }

    @Test
    public void testMissingSource() {
        // Given: Ownership validation requires an absent field
        stubAccounts("123");
        stubPage("123", 0, 100, records(1, 1));
        var connector = initYaml(OPERATIONS + "          sourceField: userId\n");
        var results = new ArrayList<ConnectorObject>();

        // When: Resolve records without their source identifier
        var failure = expectThrows(ConnectorException.class, () -> collect(connector, results));

        // Then: Fail without assuming source ownership
        assertTrue(messages(failure).contains("source"), messages(failure));
        assertEquals(results, List.of());
        assertRequests("GET /accounts", pageRequest("123", 0, 100));
    }

    @Test
    public void testAllowedPartials() {
        // Given: Caller permits unresolved attributes after failure
        stubAccounts("123");
        stubPage("123", 0, 100, records(1, 1));
        wireMockServer.stubFor(membership(1).willReturn(aResponse().withStatus(503)));
        var options = new OperationOptionsBuilder().setAllowPartialAttributeValues(true).build();

        // When: Resolve memberships with partial attributes allowed
        var results = search(initYaml(OPERATIONS), null, options);

        // Then: Return user without incomplete group values
        assertEquals(results.size(), 1);
        assertEquals(results.getFirst().getUid(), new Uid("123"));
        assertNull(results.getFirst().getAttributeByName("groups"));
        assertRequests("GET /accounts", pageRequest("123", 0, 100), pageRequest("123", 1, 100));
    }

    @Test
    public void testRepeatedPage() {
        // Given: Duplicate records occupy consecutive complete pages
        stubAccounts("123");
        stubPage("123", 0, 1, records(1, 1));
        stubPage("123", 1, 1, records(1, 1));
        stubPage("123", 2, 1, records(2, 2));
        stubPage("123", 3, 1, "[]");

        // When: Read memberships across identical adjacent pages
        var results = search(initYaml(OPERATIONS.replace("pageSize: 100", "pageSize: 1")), null);

        // Then: Preserve later unique groups without duplicates
        assertGroups(results.getFirst(), List.of("G1", "G2"));
        assertRequests("GET /accounts", pageRequest("123", 0, 1), pageRequest("123", 1, 1), pageRequest("123", 2, 1), pageRequest("123", 3, 1));
    }

    @Test
    public void testPageLimit() {
        // Given: Server ignores offsets and repeats records
        stubAccounts("123");
        stubPage("123", 0, 100, records(1, 1));
        stubPage("123", 1, 100, records(1, 1));
        var connector = initYaml(OPERATIONS + "          maxPages: 2\n");
        var results = new ArrayList<ConnectorObject>();

        // When: Reach the membership page limit
        var failure = expectThrows(ConnectorException.class, () -> collect(connector, results));

        // Then: Report exhaustion without returning partial memberships
        assertTrue(messages(failure).contains("maxPages"), messages(failure));
        assertEquals(results, List.of());
        assertRequests("GET /accounts", pageRequest("123", 0, 100), pageRequest("123", 1, 100));
    }

    @DataProvider
    public Object[][] errorStatuses() {
        return new Object[][] {{401}, {403}, {404}, {429}, {503}};
    }

    @DataProvider
    public Object[][] partialStatuses() {
        return new Object[][] {{204, ""}, {206, "{\"result\":[]}"}};
    }

    @Test(dataProvider = "partialStatuses")
    public void testIncompleteSuccess(int status, String response) {
        // Given: Later response cannot establish collection completeness
        stubAccounts("123");
        stubPage("123", 0, 100, records(1, 1));
        wireMockServer.stubFor(membership(1).willReturn(aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(response)));
        var connector = initYaml(OPERATIONS);
        var results = new ArrayList<ConnectorObject>();

        // When: Resolve memberships from an incomplete response
        var failure = expectThrows(ConnectorException.class, () -> collect(connector, results));

        // Then: Reject successful status without complete data
        assertTrue(messages(failure).contains("memberships"), messages(failure));
        assertEquals(results, List.of());
        assertRequests("GET /accounts", pageRequest("123", 0, 100), pageRequest("123", 1, 100));
    }

    @Test(dataProvider = "errorStatuses")
    public void testLaterFailure(int status) {
        // Given: A later membership request fails
        stubAccounts("123");
        stubPage("123", 0, 100, records(1, 1));
        wireMockServer.stubFor(membership(1).willReturn(aResponse().withStatus(status)));
        var connector = initYaml(OPERATIONS);
        var results = new ArrayList<ConnectorObject>();

        // When: Resolve memberships after the successful page
        var failure = expectThrows(ConnectorException.class, () -> collect(connector, results));

        // Then: Surface failure and suppress incomplete user
        assertTrue(messages(failure).contains(String.valueOf(status)), messages(failure));
        assertEquals(results, List.of());
        assertRequests("GET /accounts", pageRequest("123", 0, 100), pageRequest("123", 1, 100));
    }

    @DataProvider
    public Object[][] malformedResponses() {
        return new Object[][] {{"{}"}, {"{\"result\":null}"}, {"{\"result\":{}}"}, {"{\"result\":[null]}"}, {"{\"result\":[{}]}"},
                {"{\"result\":[{\"group\":{\"id\":null}}]}"}, {"{\"result\":[{\"group\":{\"id\":\" \"}}]}"},
                {"{\"result\":[{\"group\":{\"id\":true}}]}"}, {"{\"result\":[{\"group\":{\"id\":1.5}}]}"},
                {"{\"result\":[{\"group\":{\"id\":[]}}]}"}, {"not-json"}};
    }

    @Test(dataProvider = "malformedResponses")
    public void testMalformedResponse(String response) {
        // Given: Membership response violates the declared contract
        stubAccounts("123");
        wireMockServer.stubFor(membership(0).willReturn(okJson(response)));
        var connector = initYaml(OPERATIONS);
        var results = new ArrayList<ConnectorObject>();

        // When: Attempt resolving the malformed collection
        var failure = expectThrows(ConnectorException.class, () -> collect(connector, results));

        // Then: Fail explicitly without publishing incomplete memberships
        assertTrue(messages(failure).contains("memberships"), messages(failure));
        assertEquals(results, List.of());
        assertRequests("GET /accounts", pageRequest("123", 0, 100));
    }

    private ClassHandlerConnectorBase initYaml(String operations) {
        var connector = YamlOperationsConnector.fromStrings(operations).withSchema(SCHEMA, CONNID_SCHEMA_SCRIPT);
        connector.init(new SimpleConfig(wireMockServer.port()));
        return connector;
    }

    private void stubAccounts(String... identifiers) {
        var accounts = JsonNodeFactory.instance.arrayNode();
        for (var identifier : identifiers) {
            accounts.addObject().put("id", identifier).put("name", "User " + identifier);
        }
        wireMockServer.stubFor(get(urlEqualTo("/accounts")).willReturn(okJson(accounts.toString())));
    }

    private void stubPage(String source, int offset, int limit, String records) {
        wireMockServer.stubFor(get(urlPathEqualTo("/memberships")).withQueryParam("user", equalTo(source))
                .withQueryParam("offset", equalTo(String.valueOf(offset))).withQueryParam("limit", equalTo(String.valueOf(limit)))
                .willReturn(okJson("{\"result\":" + records + "}")));
    }

    private MappingBuilder membership(int offset) {
        return get(urlPathEqualTo("/memberships")).withQueryParam("user", equalTo("123"))
                .withQueryParam("offset", equalTo(String.valueOf(offset))).withQueryParam("limit", equalTo("100"));
    }

    private String records(int first, int last) {
        var records = JsonNodeFactory.instance.arrayNode();
        for (int number = first; number <= last; number++) {
            records.addObject().putObject("group").put("id", "G" + number);
        }
        return records.toString();
    }

    private void assertGroups(ConnectorObject account, List<String> expected) {
        var groups = account.getAttributeByName("groups");
        assertNotNull(groups, "The complete relationship attribute must be present");
        var actual = new ArrayList<String>();
        for (var value : groups.getValue()) {
            assertTrue(value instanceof ConnectorObjectReference, "Expected a typed reference: " + value);
            var reference = (ConnectorObjectReference) value;
            assertEquals(reference.getValue().getObjectClass(), new ObjectClass("Group"));
            assertFalse(reference.hasObject(), "Membership records contain identification, not group objects");
            actual.add(AttributeUtil.getUidAttribute(reference.getValue().getAttributes()).getUidValue());
        }
        assertEquals(actual, expected);
    }

    private String pageRequest(String source, int offset, int limit) {
        return "GET /memberships?user=" + source + "&offset=" + offset + "&limit=" + limit;
    }

    private void collect(ClassHandlerConnectorBase connector, List<ConnectorObject> results) {
        connector.executeQuery(new ObjectClass("Account"), null, account -> { results.add(account); return true; }, new OperationOptionsBuilder().build());
    }

    private void assertRequests(String... expected) {
        var actual = wireMockServer.getAllServeEvents().stream()
                .map(event -> normalize(event.getRequest().getMethod() + " " + event.getRequest().getUrl())).sorted().toList();
        assertEquals(actual, Arrays.stream(expected).map(RelationshipReadTest::normalize).sorted().toList());
    }

    private static String normalize(String request) {
        var parts = request.split("\\?", 2);
        return parts.length == 1 ? request : parts[0] + "?" + String.join("&", Arrays.stream(parts[1].split("&")).sorted().toList());
    }

    private String messages(Throwable failure) {
        var messages = new StringBuilder();
        while (failure != null) {
            messages.append(failure.getMessage()).append('\n');
            failure = failure.getCause();
        }
        return messages.toString();
    }
}
