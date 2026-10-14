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
import static org.testng.Assert.expectThrows;
import static org.testng.AssertJUnit.assertTrue;

/**
 * Verifies the declarative {@code request { header(…) }} block (and its YAML counterpart
 * {@code request: { headers: … }}) that adds static headers to every request an
 * endpoint issues — search and CRUD alike — without any Groovy beyond the literal values.
 */
public class DeclarativeRequestHeaderTest extends AbstractCrudConnectorTest {

    /** Updates read the object first, so the by-id search document is loaded alongside. */
    private static final String SEARCH_BY_ID_YAML = """
            objectClasses:
              Account:
                search:
                  endpoints:
                    - path: accounts/{id}
                      singleResult: true
                      supportedFilters:
                        - spec: |
                            attribute("id").eq().anySingleValue()
                          request: |
                            request.pathParameter("id", value)
            """;

    @Test
    public void searchHeadersFromGroovyAreAddedToRequest() {
        wireMockServer.stubFor(get(urlPathEqualTo(ACCOUNTS_PATH))
                .willReturn(okJson("[{\"id\":\"1\",\"name\":\"one\"}]")));

        var script = """
                objectClass("Account") {
                    search {
                        endpoint("accounts") {
                            responseFormat JSON_ARRAY
                            emptyFilterSupported true
                            request {
                                header("X-Tenant-Id", "acme")
                                header("X-Api-Version", "2025-01-01")
                            }
                        }
                    }
                }
                """;

        var results = search(initConnector(script), null);

        assertEquals(results.size(), 1);
        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(ACCOUNTS_PATH))
                .withHeader("X-Tenant-Id", equalTo("acme"))
                .withHeader("X-Api-Version", equalTo("2025-01-01"))).size(), 1);
    }

    @Test
    public void searchHeadersFromYamlAreAddedToRequest() {
        wireMockServer.stubFor(get(urlPathEqualTo(ACCOUNTS_PATH))
                .willReturn(okJson("[{\"id\":\"1\",\"name\":\"one\"}]")));

        var yaml = """
                objectClasses:
                  Account:
                    search:
                      endpoints:
                        - path: accounts
                          responseFormat: JSON_ARRAY
                          emptyFilterSupported: true
                          request:
                            headers:
                              X-Tenant-Id: acme
                              X-Api-Version: "2025-01-01"
                """;

        var results = search(yamlConnector(yaml), null);

        assertEquals(results.size(), 1);
        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(ACCOUNTS_PATH))
                .withHeader("X-Tenant-Id", equalTo("acme"))
                .withHeader("X-Api-Version", equalTo("2025-01-01"))).size(), 1);
    }

    @Test
    public void postSearchYamlRequestCarriesHeadersAndContentType() {
        wireMockServer.stubFor(post(urlPathEqualTo(ACCOUNTS_PATH))
                .willReturn(okJson("{\"id\":\"1\",\"name\":\"one\"}")));

        var yaml = """
                objectClasses:
                  Account:
                    search:
                      endpoints:
                        - path: accounts
                          method: POST
                          emptyFilterSupported: true
                          request:
                            contentType: APPLICATION_JSON
                            headers:
                              X-Tenant-Id: acme
                """;

        var results = search(yamlConnector(yaml), null);

        assertEquals(results.size(), 1);
        var requests = wireMockServer.findAll(postRequestedFor(urlPathEqualTo(ACCOUNTS_PATH))
                .withHeader("X-Tenant-Id", equalTo("acme")));
        assertEquals(requests.size(), 1);
        assertEquals(requests.getFirst().getHeader("Content-Type"), "application/json");
    }

    @Test
    public void createHeadersFromYamlAreAddedToRequest() {
        wireMockServer.stubFor(post(urlPathEqualTo(ACCOUNTS_PATH))
                .willReturn(aResponse().withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"123\",\"name\":\"created\"}")));

        var yaml = """
                objectClasses:
                  Account:
                    create:
                      endpoints:
                        - method: POST
                          path: accounts
                          request:
                            contentType: APPLICATION_JSON
                            headers:
                              X-Tenant-Id: acme
                """;

        var connector = yamlConnector(yaml);
        connector.create(new ObjectClass("Account"),
                Set.of(new Name("test")),
                new OperationOptionsBuilder().build());

        assertEquals(wireMockServer.findAll(postRequestedFor(urlPathEqualTo(ACCOUNTS_PATH))
                .withHeader("X-Tenant-Id", equalTo("acme"))).size(), 1);
    }

    @Test
    public void updateHeadersFromYamlAreAddedToRequest() {
        wireMockServer.stubFor(get(urlEqualTo(ACCOUNT_BY_ID_PATH))
                .willReturn(okJson("{\"id\":\"123\",\"name\":\"old-name\"}")));
        wireMockServer.stubFor(patch(urlPathEqualTo(ACCOUNT_BY_ID_PATH))
                .willReturn(okJson("{\"id\":\"123\",\"name\":\"new-name\"}")));

        var yaml = """
                objectClasses:
                  Account:
                    update:
                      endpoints:
                        - method: PATCH
                          path: accounts/{id}
                          request:
                            headers:
                              X-Tenant-Id: acme
                """;

        var connector = initYaml(SEARCH_BY_ID_YAML, yaml);
        connector.updateDelta(new ObjectClass("Account"),
                new Uid("123"),
                Set.of(AttributeDeltaBuilder.build(Name.NAME, List.of("new-name"))),
                new OperationOptionsBuilder().build());

        assertEquals(wireMockServer.findAll(patchRequestedFor(urlPathEqualTo(ACCOUNT_BY_ID_PATH))
                .withHeader("X-Tenant-Id", equalTo("acme"))).size(), 1);
    }

    @Test
    public void deleteHeadersFromYamlAreAddedToRequest() {
        wireMockServer.stubFor(delete(urlPathEqualTo(ACCOUNT_BY_ID_PATH))
                .willReturn(aResponse().withStatus(204)));

        var yaml = """
                objectClasses:
                  Account:
                    delete:
                      endpoints:
                        - method: DELETE
                          path: accounts/{id}
                          request:
                            headers:
                              X-Audit-Ref: scimrest
                """;

        var connector = yamlConnector(yaml);
        connector.delete(new ObjectClass("Account"), new Uid("123"), new OperationOptionsBuilder().build());

        assertEquals(wireMockServer.findAll(deleteRequestedFor(urlPathEqualTo(ACCOUNT_BY_ID_PATH))
                .withHeader("X-Audit-Ref", equalTo("scimrest"))).size(), 1);
    }

    @Test
    public void yamlHeaderNonScalarValueFails() {
        var yaml = """
                objectClasses:
                  Account:
                    search:
                      endpoints:
                        - path: accounts
                          emptyFilterSupported: true
                          request:
                            headers:
                              X-Api-Version:
                                nested: map
                """;

        var connector = YamlOperationsConnector.fromStrings(yaml);
        connector.init(new SimpleConfig(wireMockServer.port()));
        var exception = expectThrows(ConnectorException.class, () -> search(connector, null));
        assertTrue(exception.getMessage(),
                exception.getMessage().contains("The header 'X-Api-Version' requires a scalar value"));
    }

    private ClassHandlerConnectorBase yamlConnector(String yamlDocument) {
        var connector = YamlOperationsConnector.fromStrings(yamlDocument);
        connector.init(new SimpleConfig(wireMockServer.port()));
        return connector;
    }

    private ClassHandlerConnectorBase initYaml(String... yamlDocuments) {
        var connector = YamlOperationsConnector.fromStrings(yamlDocuments);
        connector.init(new SimpleConfig(wireMockServer.port()));
        return connector;
    }
}
