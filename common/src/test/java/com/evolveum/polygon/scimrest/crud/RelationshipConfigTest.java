/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 */
package com.evolveum.polygon.scimrest.crud;

import com.evolveum.polygon.conndev.groovy.ScriptError;
import com.evolveum.polygon.conndev.yaml.GroovyScriptCompiler;
import com.evolveum.polygon.conndev.yaml.decl.GroovySyntaxChecker;
import com.evolveum.polygon.conndev.yaml.decl.LocatedDocument;
import com.evolveum.polygon.scimrest.groovy.api.RestSearchOperationBuilder;
import com.evolveum.polygon.scimrest.support.AbstractCrudConnectorTest;
import com.evolveum.polygon.scimrest.support.YamlOperationsConnector;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.StringReader;
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
                {"endpoint: /memberships", "endpoint: ''", "endpoint", "<not configured>"},
                {"endpoint: /memberships", "", "endpoint", "<not configured>"},
                {"sourceParameter: user", "sourceParameter: ''", "source", "/memberships"},
                {"sourceParameter: user", "", "source", "/memberships"},
                {"targetField: {type: JSON_POINTER, value: /group/id}", "", "targetField", "/memberships"},
                {"targetField: {type: JSON_POINTER, value: /group/id}", "targetField: '$.[broken'", "path", "/memberships"},
                {"objectsPath: {type: JSON_POINTER, value: /result}", "objectsPath: {type: JSON_POINTER, value: invalid}", "path", "/memberships"},
                {"sourceParameter: user", "sourceParameter: user\n          sourceTemplate: fixed", "sourceTemplate", "/memberships"},
                {"sourceParameter: user", "sourceParameter: user\n          sourceTemplate: '{uid}-{other}'", "sourceTemplate", "/memberships"},
                {"pageSize: 100", "pageSize: 0", "pageSize", "/memberships"},
                {"pageSize: 100", "pageSize: -1", "pageSize", "/memberships"},
                {"sourceParameter: user", "sourceParameter: user\n          maxPages: 0", "maxPages", "/memberships"},
                {"sourceParameter: user", "sourceParameter: user\n          initialOffset: -1", "initialOffset", "/memberships"},
                {"endpoint: /memberships", "endpoint: /users/{unknown}/memberships", "endpoint", "/users/{unknown}/memberships"},
                {"sourceParameter: user", "sourceParameter: 'user&admin=true'", "sourceParameter", "/memberships"},
                {"endpoint: /memberships", "endpoint: https://example.invalid/memberships", "endpoint", "https://example.invalid/memberships"},
                {"endpoint: /memberships", "endpoint: /users/%2e%2e/memberships", "endpoint", "/users/%2e%2e/memberships"},
                {"endpoint: /memberships", "endpoint: /memberships?user=other", "endpoint", "/memberships?user=other"},
                {"endpoint: /memberships", "endpoint: null", "requires endpoint", "<not configured>"},
                {"endpoint: /memberships", "endpoint: /memberships/%zz", "invalid percent escape", "/memberships/%zz"},
                {"endpoint: /memberships", "endpoint: /", "identify a collection", "/"}
        };
    }

    @Test(dataProvider = "invalidSettings")
    public void testInvalidSetting(String original, String replacement, String expected, String endpoint) {
        // Given: An invalid relationship retrieval declaration
        var operations = RelationshipReadTest.OPERATIONS.replace(original, replacement);
        var connector = YamlOperationsConnector.fromStrings(operations).withSchema(RelationshipReadTest.SCHEMA, CONNID_SCHEMA_SCRIPT);
        connector.init(new SimpleConfig(wireMockServer.port()));

        // When: Build handlers for the first search
        var failure = expectThrows(ConnectorException.class, () -> search(connector, null));

        // Then: Reject configuration before any HTTP request
        assertTrue(messages(failure).toLowerCase().contains(expected.toLowerCase()), messages(failure));
        assertContext(failure, "groups", endpoint);
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
        assertContext(failure, "groups", "/memberships");
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
        assertContext(failure, "groups", "/memberships");
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
        assertContext(failure, "groups", "/memberships");
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
        assertContext(failure, "groups", "/memberships");
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
        assertContext(failure, "groups", "/memberships");
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
        assertContext(failure, "groups", "/memberships");
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
        assertContext(failure, "groups", "/memberships");
        assertEquals(wireMockServer.getAllServeEvents(), List.of());
    }

    @DataProvider
    public Object[][] invalidYaml() {
        return new Object[][] {
                {"misspelled: true\n", "misspelled"},
                {"maxPages: several\n", "several"},
                {"targetField: {type: INVALID, value: /group}\n", "INVALID"},
                {"pagingSupport:\n  pageSize: [2]\n", "scalar"},
                {"pagingSupport:\n  parameters: []\n", "mapping"},
                {"pagingSupport:\n  parameters:\n    offset: query\n", "mapping"},
                {"pagingSupport:\n  parameters:\n    offset: {name: offset}\n", "'in'"},
                {"pagingSupport:\n  parameters:\n    offset: {in: query, name: []}\n", "'name'"},
                {"pagingSupport:\n  parameters:\n    offset: {in: query, surprise: offset}\n", "surprise"},
                {"pagingSupport:\n  mystery: value\n", "mystery"},
                {"pagingSupport: []\n", "The 'pagingSupport' key requires a mapping (pageSize / parameters) at "}
        };
    }

    @Test(dataProvider = "invalidYaml")
    public void testYamlContext(String settings, String expected) {
        // Given: Invalid fields precede the endpoint
        var operations = operations(settings + "endpoint: /late-memberships\n");
        var connector = YamlOperationsConnector.fromStrings(operations).withSchema(RelationshipReadTest.SCHEMA, CONNID_SCHEMA_SCRIPT);
        connector.init(new SimpleConfig(wireMockServer.port()));

        // When: Bind the relationship YAML declaration
        var failure = expectThrows(ConnectorException.class, () -> search(connector, null));

        // Then: Preserve context, cause, and source
        assertContext(failure, "groups", "/late-memberships");
        assertTrue(messages(failure).contains(expected), messages(failure));
        assertTrue(messages(failure).contains("inline document"), messages(failure));
        assertTrue(failure.getCause() != null);
        assertTrue(wireMockServer.getAllServeEvents().isEmpty());
    }

    @Test
    public void testEndpointShape() {
        // Given: Endpoint is a YAML collection
        var operations = operations("endpoint: [memberships]\n");
        var connector = YamlOperationsConnector.fromStrings(operations).withSchema(RelationshipReadTest.SCHEMA, CONNID_SCHEMA_SCRIPT);
        connector.init(new SimpleConfig(wireMockServer.port()));

        // When: Build after existing string coercion
        var failure = expectThrows(ConnectorException.class, () -> search(connector, null));

        // Then: Reject the resulting missing endpoint
        assertContext(failure, "groups", "<not configured>");
        assertTrue(messages(failure).contains("requires endpoint"), messages(failure));
        assertTrue(wireMockServer.getAllServeEvents().isEmpty());
    }

    @Test
    public void testBlockShape() {
        // Given: Relationships use an unsupported YAML sequence
        var operations = operations("").replace("relationshipRecords:\n        groups:\n", "relationshipRecords: []\n");
        var connector = YamlOperationsConnector.fromStrings(operations).withSchema(RelationshipReadTest.SCHEMA, CONNID_SCHEMA_SCRIPT);
        connector.init(new SimpleConfig(wireMockServer.port()));

        // When: Bind the malformed relationship block
        var failure = expectThrows(ConnectorException.class, () -> search(connector, null));

        // Then: Report unavailable context and structural error
        assertContext(failure, "<not configured>", "<not configured>");
        assertTrue(messages(failure).contains("Expected a mapping"), messages(failure));
        assertTrue(wireMockServer.getAllServeEvents().isEmpty());
    }

    @Test
    public void testUnknownGroovy() {
        // Given: Unknown attribute prevents endpoint declaration
        var connector = initConnector(RelationshipReadTest.SCHEMA, CONNID_SCHEMA_SCRIPT, """
                objectClass('Account') { search { relationshipRecords('missing') { endpoint '/memberships' } } }
                """);

        // When: Resolve the relationship attribute
        var failure = expectThrows(ConnectorException.class, () -> search(connector, null));

        // Then: Identify attribute before endpoint becomes available
        assertContext(failure, "missing", "<not configured>");
        assertTrue(messages(failure).contains("not found"), messages(failure));
        assertTrue(wireMockServer.getAllServeEvents().isEmpty());
    }

    private String operations(String settings) {
        return """
                objectClasses:
                  Account:
                    search:
                      endpoints:
                        - path: accounts
                          responseFormat: JSON_ARRAY
                          emptyFilterSupported: true
                      relationshipRecords:
                        groups:
                """ + settings.indent(10);
    }

    @Test
    public void testSyntaxContext() {
        // Given: Broken script precedes its relationship endpoint
        var document = LocatedDocument.parse("relationships.yaml", new StringReader("""
                relationshipRecords:
                  groups:
                    pagingSupport: |
                      return (
                    endpoint: /memberships
                """));
        var compiler = new GroovyScriptCompiler(new SimpleConfig(wireMockServer.port()).groovyContext());

        // When: Validate syntax through the operation interface
        var errors = GroovySyntaxChecker.check(document, RestSearchOperationBuilder.class, compiler);

        // Then: Preserve syntax diagnostics and relationship context
        assertEquals(errors.size(), 1);
        assertEquals(errors.getFirst().phase(), ScriptError.Phase.COMPILE);
        assertEquals(errors.getFirst().line(), Integer.valueOf(4));
        assertEquals(errors.getFirst().source(), "relationshipRecords.groups [Relationship records for attribute 'groups' at endpoint '/memberships'].pagingSupport");
        assertTrue(wireMockServer.getAllServeEvents().isEmpty());
    }

    private void assertContext(Throwable failure, String attribute, String endpoint) {
        assertTrue(messages(failure).contains("attribute '" + attribute + "' at endpoint '" + endpoint + "'"), messages(failure));
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
