/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 */
package com.evolveum.polygon.scimrest.crud;

import com.evolveum.polygon.scimrest.support.AbstractCrudConnectorTest;
import com.evolveum.polygon.scimrest.support.YamlOperationsConnector;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.AttributeDeltaBuilder;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.Uid;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;

import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public class RecordConfigTest extends AbstractCrudConnectorTest {

    private static final String SCHEMA = """
            objectClass("Account") {
                attribute("id") { jsonType "string" }
                attribute("name") { jsonType "string" }
                attribute("groups") { jsonType "string"; multiValued true }
            }
            """;

    private static final String SETTINGS = """
            endpoint: /memberships
            sourceField: userId
            targetField: groupId
            idField: id
            """;

    private static final String PAGING = """
            pagingSupport:
              pageSize: 2
              parameters:
                offset: {in: query, name: offset}
                pageSize: {in: query, name: limit}
            """;

    @DataProvider
    public Object[][] invalidDeclarations() {
        return new Object[][] {
                {SETTINGS.replace("endpoint: /memberships\n", ""), "requires endpoint"},
                {SETTINGS.replace("sourceField: userId\n", ""), "requires sourceField"},
                {SETTINGS.replace("targetField: groupId\n", ""), "requires targetField"},
                {SETTINGS.replace("idField: id\n", ""), "requires idField"},
                {SETTINGS.replace("sourceField: userId", "sourceField: '$'"), "sourceField must select"},
                {SETTINGS.replace("sourceField: userId", "sourceField: '$.users[0].id'"), "sourceField must contain only"},
                {SETTINGS.replace("targetField: groupId", "targetField: '$.groups[0].id'"), "targetField must contain only"},
                {SETTINGS.replace("sourceField: userId", "sourceField: '$.link'").replace("targetField: groupId", "targetField: '$.link.group'"), "must not overlap"},
                {SETTINGS.replace("targetField: groupId", "targetField: userId"), "must not overlap"},
                {SETTINGS.replace("idField: id", "idField: userId"), "must not overlap"},
                {SETTINGS.replace("idField: id", "idField: groupId"), "must not overlap"},
                {SETTINGS.replace("idField: id", "idField: '$.'"), "Invalid idField"},
                {SETTINGS + "objectsPath: '$.'\n", "Invalid objectsPath"},
                {SETTINGS + "sourceType: number\n", "sourceType must be"},
                {SETTINGS + "targetType: boolean\n", "targetType must be"},
                {SETTINGS + "initialOffset: -1\n", "initialOffset"},
                {SETTINGS + "maxPages: 0\n", "maxPages must be positive"},
                {SETTINGS + "sourceParameter: 'user=name'\n", "sourceParameter must be"},
                {SETTINGS.replace("/memberships", "https://example.test/memberships"), "relative API path"},
                {SETTINGS.replace("/memberships", "/memberships?user=other"), "without a query or fragment"},
                {SETTINGS.replace("/memberships", "/memberships/../users"), "traversal segments"},
                {SETTINGS.replace("/memberships", "/memberships/%2e%2e/users"), "traversal segments"},
                {SETTINGS.replace("/memberships", "/memberships/{id}"), "unsupported placeholder"},
                {SETTINGS + "deleteEndpoint: /memberships\n", "deleteEndpoint must contain {id}"},
                {SETTINGS + "deleteEndpoint: /memberships/{other}/{id}\n", "unsupported placeholder"}
        };
    }

    @Test(dataProvider = "invalidDeclarations")
    public void testInvalidDeclarations(String settings, String message) {
        // Given: Invalid relationship configuration
        var operations = operations(settings);

        // When: Build handlers for membership update
        var exception = expectThrows(ConfigurationException.class, () -> update(SCHEMA, operations));

        // Then: Explain failure before HTTP requests
        assertTrue(exception.getMessage().contains(message), exception.getMessage());
        assertTrue(wireMockServer.getAllServeEvents().isEmpty());
    }

    @DataProvider
    public Object[][] invalidPaging() {
        return new Object[][] {
                {PAGING.replace("pageSize: 2", "pageSize: 0"), "pageSize"},
                {PAGING.replace("pageSize: 2", "pageSize: -1"), "pageSize"},
                {PAGING.replace("  pageSize: 2\n", ""), "positive pageSize"},
                {"pagingSupport:\n  pageSize: 2\n", "offset and pageSize"},
                {PAGING.replace("    offset: {in: query, name: offset}\n", ""), "offset and pageSize"},
                {PAGING.replace("    pageSize: {in: query, name: limit}\n", ""), "offset and pageSize"},
                {PAGING.replace("offset: {in: query, name: offset}", "page: {in: query, name: page}"), "offset and pageSize"},
                {PAGING.replace("offset: {in: query, name: offset}", "cursor: {in: query, name: cursor}"), "token"},
                {PAGING.replace("in: query, name: offset", "in: body, name: offset"), "POST"},
                {PAGING.replace("in: query, name: offset", "in: cookie, name: offset"), "location"},
                {PAGING.replace("name: limit", "name: offset"), "distinct"},
                {PAGING + "sourceParameter: offset\n", "distinct"},
                {PAGING + "sourceParameter: limit\n", "distinct"},
                {PAGING.replace("in: query, name: offset", "in: header, name: X-Page")
                        .replace("in: query, name: limit", "in: header, name: x-page"), "distinct"},
                {PAGING.replace("name: offset", "name: 'offset&user'"), "name"},
                {PAGING.replace("name: limit", "name: ''"), "name"},
                {"pagingSupport: 'request.queryParameter(\"offset\", 0)'\n", "declarative pagingSupport"}
        };
    }

    @Test(dataProvider = "invalidPaging")
    public void testInvalidPaging(String settings, String expected) {
        // Given: Membership paging violates its supported contract
        var operations = operations(SETTINGS + settings);

        // When: Build the relationship paging handler
        var failure = expectThrows(ConnectorException.class, () -> update(SCHEMA, operations));

        // Then: Reject invalid paging before remote access
        assertTrue(messages(failure).contains(expected), messages(failure));
        assertTrue(wireMockServer.getAllServeEvents().isEmpty());
    }

    @DataProvider
    public Object[][] obsoleteSettings() {
        return new Object[][] {{"recordsPath", "'$.result'"}, {"offsetParameter", "offset"}, {"limitParameter", "limit"}, {"pageSize", "2"}};
    }

    @Test(dataProvider = "obsoleteSettings")
    public void testObsoleteSetting(String name, String value) {
        // Given: Configuration uses an obsolete membership key
        var operations = operations(SETTINGS + name + ": " + value + "\n");

        // When: Load the obsolete YAML declaration
        var failure = expectThrows(ConnectorException.class, () -> update(SCHEMA, operations));

        // Then: Report obsolete configuration without remote requests
        assertTrue(messages(failure).contains(name), messages(failure));
        assertTrue(messages(failure).toLowerCase().contains("unknown"), messages(failure));
        assertTrue(wireMockServer.getAllServeEvents().isEmpty());
    }

    @Test
    public void testDuplicateToken() {
        // Given: Groovy repeats the offset token mapping
        var operations = """
                objectClass('Account') {
                    update {
                        relationshipRecords('groups') {
                            endpoint '/memberships'
                            sourceField 'userId'
                            targetField 'groupId'
                            idField 'id'
                            pageSize 2
                            pagingParameter 'offset', 'query', 'offset'
                            pagingParameter 'pageSize', 'query', 'limit'
                            pagingParameter 'offset', 'header', 'X-Offset'
                        }
                    }
                }
                """;
        var connector = initConnector(SCHEMA, CONNID_SCHEMA_SCRIPT, operations);
        var delta = AttributeDeltaBuilder.build("groups", List.of("A"));

        // When: Build the membership paging parameter mappings
        var failure = expectThrows(ConnectorException.class, () -> connector.updateDelta(new ObjectClass("Account"), new Uid("123"), Set.of(delta),
                new OperationOptionsBuilder().build()));

        // Then: Reject competing mappings before remote requests
        assertTrue(messages(failure).contains("duplicate paging token"), messages(failure));
        assertTrue(messages(failure).contains("attribute 'groups' at endpoint '/memberships'"), messages(failure));
        assertTrue(wireMockServer.getAllServeEvents().isEmpty());
    }

    @DataProvider
    public Object[][] invalidAttributes() {
        return new Object[][] {
                {SCHEMA.replace("multiValued true", "multiValued false"), "multivalued, updateable"},
                {SCHEMA.replace("multiValued true", "multiValued true; updatable false"), "multivalued, updateable"},
                {SCHEMA.replace("attribute(\"groups\") { jsonType \"string\"", "attribute(\"groups\") { jsonType \"boolean\""), "string, integral, or reference"}
        };
    }

    @Test(dataProvider = "invalidAttributes")
    public void testInvalidAttributes(String schema, String message) {
        // Given: Unsupported membership attribute definition
        var operations = operations(SETTINGS);

        // When: Build handlers for membership update
        var exception = expectThrows(ConfigurationException.class, () -> update(schema, operations));

        // Then: Reject incompatible schema without requests
        assertTrue(exception.getMessage().contains(message), exception.getMessage());
        assertTrue(wireMockServer.getAllServeEvents().isEmpty());
    }

    @Test
    public void testUnknownAttribute() {
        // Given: Declaration references an unknown attribute
        var operations = operations(SETTINGS).replace("groups:", "missing:");

        // When: Build handlers for membership update
        var exception = expectThrows(ConfigurationException.class, () -> update(SCHEMA, operations));

        // Then: Identify missing attribute without requests
        assertTrue(exception.getMessage().contains("missing"), exception.getMessage());
        assertTrue(exception.getMessage().contains("not found"), exception.getMessage());
        assertTrue(wireMockServer.getAllServeEvents().isEmpty());
    }

    @Test
    public void testDuplicateAliases() {
        // Given: Two declarations share one attribute
        var schema = SCHEMA.replace("multiValued true", "multiValued true; connId { name 'memberships' }");
        var operations = operations(SETTINGS) + "        memberships:\n" + SETTINGS.indent(10);

        // When: Build handlers for membership update
        var exception = expectThrows(ConfigurationException.class, () -> update(schema, operations));

        // Then: Reject ambiguous routing before requests
        assertTrue(exception.getMessage().contains("Multiple relationship record declarations"), exception.getMessage());
        assertTrue(exception.getMessage().contains("memberships"), exception.getMessage());
        assertTrue(wireMockServer.getAllServeEvents().isEmpty());
    }

    private String operations(String settings) {
        return "objectClasses:\n  Account:\n    update:\n      relationshipRecords:\n        groups:\n" + settings.indent(10);
    }

    private void update(String schema, String operations) {
        var connector = YamlOperationsConnector.fromStrings(operations).withSchema(schema, CONNID_SCHEMA_SCRIPT);
        connector.init(new SimpleConfig(wireMockServer.port()));
        connector.updateDelta(new ObjectClass("Account"), new Uid("123"), Set.of(AttributeDeltaBuilder.build("groups", List.of("A"))),
                new OperationOptionsBuilder().build());
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
