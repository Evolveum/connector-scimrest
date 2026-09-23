/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.crud;

import com.evolveum.polygon.conndev.spi.ClassHandlerConnectorBase;
import com.evolveum.polygon.scimrest.support.AbstractCrudConnectorTest;
import com.evolveum.polygon.scimrest.support.YamlOperationsConnector;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.testng.annotations.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.expectThrows;
import static org.testng.AssertJUnit.assertTrue;

/**
 * Verifies the declarative (path-based) form of the search {@code objectExtractor}: a
 * {@code {type, value}} mapping with a JSON Pointer or basic JSONPath expression extracts the
 * list of objects from a deeper response structure without any Groovy.
 */
public class DeclarativeObjectExtractorTest extends AbstractCrudConnectorTest {

    private static final String EMBEDDED_BODY = """
            {"_embedded": {"elements": [{"id": "1", "name": "first"}, {"id": "2", "name": "second"}]}, "total": 2}
            """;

    private ClassHandlerConnectorBase yamlConnector(String endpoints) {
        var yaml = "objectClasses:\n  Account:\n    search:\n      endpoints:\n" + endpoints;
        var connector = YamlOperationsConnector.fromStrings(yaml);
        connector.init(new SimpleConfig(wireMockServer.port()));
        return connector;
    }

    @Test
    public void objectExtractorByJsonPointer() {
        wireMockServer.stubFor(get(urlEqualTo(ACCOUNTS_PATH)).willReturn(okJson(EMBEDDED_BODY)));

        var results = search(yamlConnector("""
                - path: accounts
                  responseFormat: JSON_OBJECT
                  emptyFilterSupported: true
                  objectExtractor:
                    type: JSON_POINTER
                    value: /_embedded/elements
        """), null);

        assertEquals(results.size(), 2);
        assertEquals(results.getFirst().getUid().getUidValue(), "1");
    }

    @Test
    public void objectExtractorByJsonPath() {
        wireMockServer.stubFor(get(urlEqualTo(ACCOUNTS_PATH)).willReturn(okJson(EMBEDDED_BODY)));

        var results = search(yamlConnector("""
                - path: accounts
                  responseFormat: JSON_OBJECT
                  emptyFilterSupported: true
                  objectExtractor:
                    type: JSON_PATH
                    value: $._embedded.elements
        """), null);

        assertEquals(results.size(), 2);
        assertEquals(results.getLast().getUid().getUidValue(), "2");
    }

    @Test
    public void objectExtractorPathDefaultsToJsonPath() {
        wireMockServer.stubFor(get(urlEqualTo(ACCOUNTS_PATH)).willReturn(okJson(EMBEDDED_BODY)));

        var results = search(yamlConnector("""
                - path: accounts
                  responseFormat: JSON_OBJECT
                  emptyFilterSupported: true
                  objectExtractor:
                    value: $._embedded.elements
        """), null);

        assertEquals(results.size(), 2);
    }

    @Test
    public void objectExtractorPathToSingleObject() {
        wireMockServer.stubFor(get(urlEqualTo(ACCOUNT_BY_ID_PATH))
                .willReturn(okJson("{\"user\": {\"id\": \"123\", \"name\": \"by-id\"}}")));

        var results = search(yamlConnector("""
                - path: accounts/{id}
                  singleResult: true
                  objectExtractor:
                    type: JSON_POINTER
                    value: /user
                  supportedFilters:
                    - spec: |
                        attribute("id").eq().anySingleValue()
                      request: |
                        request.pathParameter("id", value)
        """), FilterBuilder.equalTo(new Uid("123")));

        assertEquals(results.size(), 1);
        assertEquals(results.getFirst().getUid().getUidValue(), "123");
    }

    @Test
    public void objectExtractorPathMissingYieldsNoObjects() {
        wireMockServer.stubFor(get(urlEqualTo(ACCOUNTS_PATH))
                .willReturn(okJson("{\"other\": {\"elements\": []}}")));

        var results = search(yamlConnector("""
                - path: accounts
                  responseFormat: JSON_OBJECT
                  emptyFilterSupported: true
                  objectExtractor:
                    value: $._embedded.elements
        """), null);

        assertEquals(results.size(), 0);
    }

    @Test
    public void invalidObjectExtractorPathFailsClearly() {
        wireMockServer.stubFor(get(urlEqualTo(ACCOUNTS_PATH)).willReturn(okJson(EMBEDDED_BODY)));

        var connector = yamlConnector("""
                - path: accounts
                  emptyFilterSupported: true
                  objectExtractor:
                    type: JSON_POINTER
                    value: not-a-pointer
        """);

        var exception = expectThrows(ConnectorException.class, () -> search(connector, null));
        assertTrue(exception.getMessage(),
                exception.getMessage().contains("not-a-pointer"));
    }

    @Test
    public void objectExtractorFromGroovyStringPath() {
        wireMockServer.stubFor(get(urlEqualTo(ACCOUNTS_PATH)).willReturn(okJson(EMBEDDED_BODY)));

        var script = """
                objectClass("Account") {
                    search {
                        endpoint("accounts") {
                            responseFormat JSON_OBJECT
                            emptyFilterSupported true
                            objectExtractor '$._embedded.elements'
                        }
                    }
                }
                """;

        var results = search(initConnector(script), null);

        assertEquals(results.size(), 2);
        assertEquals(results.getFirst().getUid().getUidValue(), "1");
    }
}
