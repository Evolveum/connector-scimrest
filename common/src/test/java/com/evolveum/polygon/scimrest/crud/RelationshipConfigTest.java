/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 */
package com.evolveum.polygon.scimrest.crud;

import com.evolveum.polygon.scimrest.support.AbstractCrudConnectorTest;
import com.evolveum.polygon.scimrest.support.YamlOperationsConnector;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
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

public class RelationshipConfigTest extends AbstractCrudConnectorTest {

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
                {SETTINGS + "recordsPath: '$.'\n", "Invalid recordsPath"},
                {SETTINGS + "sourceType: number\n", "sourceType must be"},
                {SETTINGS + "targetType: boolean\n", "targetType must be"},
                {SETTINGS + "pageSize: -1\n", "pageSize and initialOffset"},
                {SETTINGS + "initialOffset: -1\n", "pageSize and initialOffset"},
                {SETTINGS + "maxPages: 0\n", "maxPages must be positive"},
                {SETTINGS + "offsetParameter: 'offset&user'\n", "offsetParameter must be"},
                {SETTINGS + "limitParameter: ''\n", "limitParameter must be"},
                {SETTINGS + "sourceParameter: 'user=name'\n", "sourceParameter must be"},
                {SETTINGS + "pageSize: 10\noffsetParameter: limit\n", "parameter names must be distinct"},
                {SETTINGS + "pageSize: 10\nsourceParameter: offset\n", "parameter names must be distinct"},
                {SETTINGS + "pageSize: 10\nsourceParameter: limit\n", "parameter names must be distinct"},
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
}
