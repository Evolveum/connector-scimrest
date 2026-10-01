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
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.Uid;
import org.testng.annotations.Test;

import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.expectThrows;
import static org.testng.AssertJUnit.assertTrue;

/**
 * Verifies the declarative {@code request { queryParameter(…) }} block (and its YAML counterpart
 * {@code request: { queryParameters: … }}) that adds static query parameters to every request an
 * endpoint issues — search and CRUD alike — without any Groovy beyond the literal values.
 */
public class DeclarativeQueryParameterTest extends AbstractCrudConnectorTest {

    @Test
    public void searchQueryParametersFromGroovyAreAddedToRequest() {
        wireMockServer.stubFor(get(urlPathEqualTo(ACCOUNTS_PATH))
                .willReturn(okJson("[{\"id\":\"1\",\"name\":\"one\"}]")));

        var script = """
                objectClass("Account") {
                    search {
                        endpoint("accounts") {
                            responseFormat JSON_ARRAY
                            emptyFilterSupported true
                            request {
                                queryParameter("jql", "created >= 1970-01-01 ORDER BY created ASC")
                                queryParameter("fields", "summary,status,project,created")
                            }
                        }
                    }
                }
                """;

        var results = search(initConnector(script), null);

        assertEquals(results.size(), 1);
        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(ACCOUNTS_PATH))
                .withQueryParam("jql", equalTo("created >= 1970-01-01 ORDER BY created ASC"))
                .withQueryParam("fields", equalTo("summary,status,project,created"))).size(), 1);
    }

    @Test
    public void searchQueryParametersAreUrlEncoded() {
        wireMockServer.stubFor(get(urlPathEqualTo(ACCOUNTS_PATH))
                .willReturn(okJson("[{\"id\":\"1\",\"name\":\"one\"}]")));

        var script = """
                objectClass("Account") {
                    search {
                        endpoint("accounts") {
                            responseFormat JSON_ARRAY
                            emptyFilterSupported true
                            request {
                                queryParameter("jql", "created >= 1970-01-01 ORDER BY created ASC")
                                queryParameter("fields", "summary,status,project,created")
                            }
                        }
                    }
                }
                """;

        search(initConnector(script), null);

        var url = wireMockServer.findAll(getRequestedFor(urlPathEqualTo(ACCOUNTS_PATH))).getFirst().getUrl();
        assertTrue(url, url.contains("jql=created+%3E%3D+1970-01-01+ORDER+BY+created+ASC"));
        assertTrue(url, url.contains("fields=summary%2Cstatus%2Cproject%2Ccreated"));
    }

    @Test
    public void searchQueryParametersFromYamlAreAddedToRequest() {
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
                            queryParameters:
                              jql: created >= 1970-01-01 ORDER BY created ASC
                              fields: summary,status,project,created
                """;

        var results = search(yamlConnector(yaml), null);

        assertEquals(results.size(), 1);
        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(ACCOUNTS_PATH))
                .withQueryParam("jql", equalTo("created >= 1970-01-01 ORDER BY created ASC"))
                .withQueryParam("fields", equalTo("summary,status,project,created"))).size(), 1);
    }

    @Test
    public void postSearchYamlRequestCarriesContentTypeAndQueryParameters() {
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
                            queryParameters:
                              jql: type in (Task, Bug)
                """;

        var results = search(yamlConnector(yaml), null);

        assertEquals(results.size(), 1);
        var requests = wireMockServer.findAll(postRequestedFor(urlPathEqualTo(ACCOUNTS_PATH))
                .withQueryParam("jql", equalTo("type in (Task, Bug)")));
        assertEquals(requests.size(), 1);
        assertEquals(requests.getFirst().getHeader("Content-Type"), "application/json");
    }

    @Test
    public void createQueryParametersFromGroovyAreAddedToRequest() {
        wireMockServer.stubFor(post(urlPathEqualTo(ACCOUNTS_PATH))
                .willReturn(aResponse().withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"123\",\"name\":\"created\"}")));

        var script = """
                objectClass("Account") {
                    create {
                        endpoint("accounts") {
                            request {
                                contentType APPLICATION_JSON
                                queryParameter("fields", "name")
                            }
                        }
                    }
                }
                """;

        var connector = initConnector(script);
        connector.create(new ObjectClass("Account"),
                Set.of(new Name("test")),
                new OperationOptionsBuilder().build());

        assertEquals(wireMockServer.findAll(postRequestedFor(urlPathEqualTo(ACCOUNTS_PATH))
                .withQueryParam("fields", equalTo("name"))).size(), 1);
    }

    @Test
    public void deleteQueryParametersFromYamlAreAddedToRequest() {
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
                            queryParameters:
                              audit: scimrest
                """;

        var connector = yamlConnector(yaml);
        connector.delete(new ObjectClass("Account"), new Uid("123"), new OperationOptionsBuilder().build());

        assertEquals(wireMockServer.findAll(deleteRequestedFor(urlPathEqualTo(ACCOUNT_BY_ID_PATH))
                .withQueryParam("audit", equalTo("scimrest"))).size(), 1);
    }

    @Test
    public void yamlQueryParameterNonScalarValueFails() {
        var yaml = """
                objectClasses:
                  Account:
                    search:
                      endpoints:
                        - path: accounts
                          emptyFilterSupported: true
                          request:
                            queryParameters:
                              jql:
                                nested: map
                """;

        var connector = YamlOperationsConnector.fromStrings(yaml);
        connector.init(new SimpleConfig(wireMockServer.port()));
        var exception = expectThrows(ConnectorException.class, () -> search(connector, null));
        assertTrue(exception.getMessage(),
                exception.getMessage().contains("The query parameter 'jql' requires a scalar value"));
    }

    private ClassHandlerConnectorBase yamlConnector(String yamlDocument) {
        var connector = YamlOperationsConnector.fromStrings(yamlDocument);
        connector.init(new SimpleConfig(wireMockServer.port()));
        return connector;
    }
}
