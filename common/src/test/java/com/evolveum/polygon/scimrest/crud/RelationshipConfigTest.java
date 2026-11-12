/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 */
package com.evolveum.polygon.scimrest.crud;

import com.evolveum.polygon.scimrest.support.AbstractCrudConnectorTest;
import com.evolveum.polygon.scimrest.support.YamlOperationsConnector;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
Checks relationship declarations before any account or membership HTTP request can execute.
*/
public class RelationshipConfigTest extends AbstractCrudConnectorTest {

    @DataProvider
    public Object[][] invalidSettings() {
        return new Object[][] {
                {"endpoint: /memberships", "endpoint: ''", "endpoint"},
                {"endpoint: /memberships", "", "endpoint"},
                {"sourceParameter: user", "sourceParameter: ''", "source"},
                {"sourceParameter: user", "", "source"},
                {"targetField: {type: JSON_POINTER, value: /group/id}", "", "targetField"},
                {"targetField: {type: JSON_POINTER, value: /group/id}", "targetField: '$.[broken'", "path"},
                {"objectsPath: {type: JSON_POINTER, value: /result}", "objectsPath: {type: JSON_POINTER, value: invalid}", "path"},
                {"sourceParameter: user", "sourceParameter: user\n          sourceTemplate: fixed", "sourceTemplate"},
                {"sourceParameter: user", "sourceParameter: user\n          sourceTemplate: '{uid}-{other}'", "sourceTemplate"},
                {"pageSize: 100", "pageSize: 0", "pageSize"},
                {"pageSize: 100", "pageSize: -1", "pageSize"},
                {"sourceParameter: user", "sourceParameter: user\n          maxPages: 0", "maxPages"},
                {"sourceParameter: user", "sourceParameter: user\n          initialOffset: -1", "initialOffset"},
                {"endpoint: /memberships", "endpoint: /users/{unknown}/memberships", "endpoint"},
                {"sourceParameter: user", "sourceParameter: 'user&admin=true'", "sourceParameter"},
                {"endpoint: /memberships", "endpoint: https://example.invalid/memberships", "endpoint"},
                {"endpoint: /memberships", "endpoint: /users/%2e%2e/memberships", "endpoint"},
                {"endpoint: /memberships", "endpoint: /memberships?user=other", "endpoint"}
        };
    }

    @Test(dataProvider = "invalidSettings")
    public void testInvalidSetting(String original, String replacement, String expected) {
        // Given: An invalid relationship retrieval declaration
        var operations = RelationshipReadTest.OPERATIONS.replace(original, replacement);
        var connector = YamlOperationsConnector.fromStrings(operations).withSchema(RelationshipReadTest.SCHEMA, CONNID_SCHEMA_SCRIPT);
        connector.init(new SimpleConfig(wireMockServer.port()));

        // When: Build handlers for the first search
        var failure = expectThrows(ConnectorException.class, () -> search(connector, null));

        // Then: Reject configuration before any HTTP request
        assertTrue(messages(failure).toLowerCase().contains(expected.toLowerCase()), messages(failure));
        assertEquals(wireMockServer.getAllServeEvents(), List.of());
    }

    @DataProvider
    public Object[][] invalidPaging() {
        return new Object[][] {
                {RelationshipReadTest.PAGING, "", "offset"},
                {"              offset: {in: query, name: offset}\n", "", "offset"},
                {"              pageSize: {in: query, name: limit}\n", "", "pageSize"},
                {"offset: {in: query, name: offset}", "page: {in: query, name: page}", "offset"},
                {"offset: {in: query, name: offset}", "cursor: {in: query, name: cursor}", "token"},
                {"offset: {in: query, name: offset}", "offset: {in: cookie, name: offset}", "location"},
                {"offset: {in: query, name: offset}", "offset: {in: body, name: offset}", "POST"},
                {"offset: {in: query, name: offset}", "offset: {in: query, name: user}", "distinct"},
                {"pageSize: {in: query, name: limit}", "pageSize: {in: query, name: offset}", "distinct"},
                {"offset: {in: query, name: offset}", "offset: {in: query, name: ''}", "name"},
                {"offset: {in: query, name: offset}", "offset: {in: query, name: 'start&admin'}", "name"},
                {RelationshipReadTest.PAGING, RelationshipReadTest.PAGING.replace("in: query, name: offset", "in: header, name: X-Page")
                        .replace("in: query, name: limit", "in: header, name: x-page"), "distinct"},
                {RelationshipReadTest.PAGING, "          pagingSupport: 'request.queryParameter(\"offset\", 0)'\n", "paging"}
        };
    }

    @Test(dataProvider = "invalidPaging")
    public void testInvalidPaging(String original, String replacement, String expected) {
        // Given: Membership paging violates its supported contract
        var operations = RelationshipReadTest.OPERATIONS.replace(original, replacement);
        var connector = YamlOperationsConnector.fromStrings(operations).withSchema(RelationshipReadTest.SCHEMA, CONNID_SCHEMA_SCRIPT);
        connector.init(new SimpleConfig(wireMockServer.port()));

        // When: Build the declarative relationship paging handler
        var failure = expectThrows(ConnectorException.class, () -> search(connector, null));

        // Then: Reject invalid paging before remote access
        assertTrue(messages(failure).toLowerCase().contains(expected.toLowerCase()), messages(failure));
        assertEquals(wireMockServer.getAllServeEvents(), List.of());
    }

    @DataProvider
    public Object[][] obsoleteSettings() {
        return new Object[][] {{"recordsPath", "'$.result'"}, {"offsetParameter", "offset"}, {"limitParameter", "limit"}, {"pageSize", "100"}};
    }

    @Test(dataProvider = "obsoleteSettings")
    public void testObsoleteSetting(String name, String value) {
        // Given: Configuration uses a replaced membership key
        var operations = RelationshipReadTest.OPERATIONS + "          " + name + ": " + value + "\n";
        var connector = YamlOperationsConnector.fromStrings(operations).withSchema(RelationshipReadTest.SCHEMA, CONNID_SCHEMA_SCRIPT);
        connector.init(new SimpleConfig(wireMockServer.port()));

        // When: Load the obsolete YAML declaration
        var failure = expectThrows(ConnectorException.class, () -> search(connector, null));

        // Then: Report the key without silently ignoring
        assertTrue(messages(failure).contains(name), messages(failure));
        assertTrue(messages(failure).toLowerCase().contains("unknown"), messages(failure));
        assertEquals(wireMockServer.getAllServeEvents(), List.of());
    }

    @Test
    public void testDuplicateToken() {
        // Given: Groovy repeats the offset token declaration
        var operations = """
                objectClass('Account') {
                    search {
                        endpoint('accounts') { responseFormat JSON_ARRAY }
                        relationshipRecords('groups') {
                            endpoint '/memberships'
                            sourceParameter 'user'
                            targetField 'groupId'
                            pagingParameter 'offset', 'query', 'offset'
                            pagingParameter 'pageSize', 'query', 'limit'
                            pagingParameter 'offset', 'header', 'X-Offset'
                        }
                    }
                }
                """;
        var connector = initConnector(RelationshipReadTest.SCHEMA, CONNID_SCHEMA_SCRIPT, operations);

        // When: Build the membership paging parameter mappings
        var failure = expectThrows(ConnectorException.class, () -> search(connector, null));

        // Then: Reject competing mappings for one token
        assertTrue(messages(failure).toLowerCase().contains("duplicate paging token"), messages(failure));
        assertTrue(messages(failure).contains("attribute 'groups' at endpoint '/memberships'"), messages(failure));
        assertEquals(wireMockServer.getAllServeEvents(), List.of());
    }

    @DataProvider
    public Object[][] invalidAttributes() {
        return new Object[][] {
                {"reference(\"groups\") { objectClass \"Group\"; multiValued true }", "", "groups"},
                {"reference(\"groups\") { objectClass \"Group\"; multiValued true }",
                        "attribute(\"groups\") { jsonType \"string\"; multiValued true }", "reference"},
                {"multiValued true", "multiValued false", "multivalued"}
        };
    }

    @Test(dataProvider = "invalidAttributes")
    public void testInvalidAttribute(String original, String replacement, String expected) {
        // Given: Target attribute violates the reference contract
        var schema = RelationshipReadTest.SCHEMA.replace(original, replacement);
        var connector = YamlOperationsConnector.fromStrings(RelationshipReadTest.OPERATIONS).withSchema(schema, CONNID_SCHEMA_SCRIPT);
        connector.init(new SimpleConfig(wireMockServer.port()));

        // When: Build the relationship attribute resolver
        var failure = expectThrows(ConnectorException.class, () -> search(connector, null));

        // Then: Reject incompatible schema without remote access
        assertTrue(messages(failure).toLowerCase().contains(expected.toLowerCase()), messages(failure));
        assertEquals(wireMockServer.getAllServeEvents(), List.of());
    }

    @Test
    public void testMissingEndpoint() {
        // Given: Relationships lack an object search endpoint
        var operations = RelationshipReadTest.OPERATIONS.replace(
                "      endpoints:\n        - path: accounts\n          responseFormat: JSON_ARRAY\n          emptyFilterSupported: true\n", "");
        var connector = YamlOperationsConnector.fromStrings(operations).withSchema(RelationshipReadTest.SCHEMA, CONNID_SCHEMA_SCRIPT);
        connector.init(new SimpleConfig(wireMockServer.port()));

        // When: Build the declared search operation
        var failure = expectThrows(ConnectorException.class, () -> search(connector, null));

        // Then: Reject unreachable relationship retrieval configuration
        assertTrue(messages(failure).contains("object search endpoint"), messages(failure));
        assertEquals(wireMockServer.getAllServeEvents(), List.of());
    }

    @Test
    public void testResolverConflict() {
        // Given: Search has competing resolvers for groups
        var operations = RelationshipReadTest.OPERATIONS + """
                      attributeResolvers:
                        - attribute: groups
                          implementation: value.addAttribute('groups', [])
                """;
        var connector = YamlOperationsConnector.fromStrings(operations).withSchema(RelationshipReadTest.SCHEMA, CONNID_SCHEMA_SCRIPT);
        connector.init(new SimpleConfig(wireMockServer.port()));

        // When: Build both declared attribute resolvers
        var failure = expectThrows(ConnectorException.class, () -> search(connector, null));

        // Then: Reject ambiguous ownership of the attribute
        assertTrue(messages(failure).contains("already has an attribute resolver"), messages(failure));
        assertEquals(wireMockServer.getAllServeEvents(), List.of());
    }

    @Test
    public void testSchemaConflict() {
        // Given: Schema already owns groups through resolution
        var schema = RelationshipReadTest.SCHEMA.replace("multiValued true",
                "multiValued true; resolver { implementation { value.addAttribute('groups', []) } }");
        var connector = YamlOperationsConnector.fromStrings(RelationshipReadTest.OPERATIONS).withSchema(schema, CONNID_SCHEMA_SCRIPT);
        connector.init(new SimpleConfig(wireMockServer.port()));

        // When: Build relationship retrieval for the attribute
        var failure = expectThrows(ConnectorException.class, () -> search(connector, null));

        // Then: Reject the competing schema resolver
        assertTrue(messages(failure).contains("another resolver"), messages(failure));
        assertEquals(wireMockServer.getAllServeEvents(), List.of());
    }

    @Test
    public void testDuplicateDeclaration() {
        // Given: Two declarations resolve the same attribute
        var operations = """
                objectClass('Account') {
                    search {
                        endpoint('accounts') { responseFormat JSON_ARRAY }
                        relationshipRecords('groups') { endpoint '/memberships'; sourceParameter 'user'; targetField 'groupId' }
                        relationshipRecords('groups') { endpoint '/other'; sourceParameter 'user'; targetField 'groupId' }
                    }
                }
                """;
        var connector = initConnector(RelationshipReadTest.SCHEMA, CONNID_SCHEMA_SCRIPT, operations);

        // When: Build duplicate relationship retrieval definitions
        var failure = expectThrows(ConnectorException.class, () -> search(connector, null));

        // Then: Reject duplicates before any remote request
        assertTrue(messages(failure).contains("Multiple relationship record declarations"), messages(failure));
        assertEquals(wireMockServer.getAllServeEvents(), List.of());
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
