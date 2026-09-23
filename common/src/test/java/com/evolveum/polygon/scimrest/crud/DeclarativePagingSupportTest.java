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
import org.testng.annotations.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.expectThrows;
import static org.testng.AssertJUnit.assertTrue;

/**
 * Verifies the declarative (token-keyed) form of the search {@code pagingSupport}: a
 * {@code pageSize} and a {@code parameters} mapping of paging tokens ({@code pageSize},
 * {@code page}, {@code offset}) onto query parameters, headers, or request body members —
 * without any Groovy.
 */
public class DeclarativePagingSupportTest extends AbstractCrudConnectorTest {

    private ClassHandlerConnectorBase yamlConnector(String endpoints) {
        var yaml = "objectClasses:\n  Account:\n    search:\n      endpoints:\n" + endpoints;
        var connector = YamlOperationsConnector.fromStrings(yaml);
        connector.init(new SimpleConfig(wireMockServer.port()));
        return connector;
    }

    @Test
    public void pagingParametersAreAddedToQuery() {
        wireMockServer.stubFor(get(urlPathEqualTo(ACCOUNTS_PATH))
                .willReturn(okJson("[{\"id\":\"1\",\"name\":\"one\"},{\"id\":\"2\",\"name\":\"two\"}]")));

        var results = search(yamlConnector("""
                - path: accounts
                  responseFormat: JSON_ARRAY
                  emptyFilterSupported: true
                  pagingSupport:
                    parameters:
                      pageSize:
                        in: query
                      page:
                        in: query
        """), null);

        assertEquals(results.size(), 2);
        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(ACCOUNTS_PATH))
                .withQueryParam("pageSize", equalTo("25"))
                .withQueryParam("page", equalTo("1"))).size(), 1);
    }

    @Test
    public void customPageSizeDrivesRequestsAndStop() {
        wireMockServer.stubFor(get(urlPathEqualTo(ACCOUNTS_PATH))
                .withQueryParam("page", equalTo("1"))
                .willReturn(okJson("[{\"id\":\"1\",\"name\":\"one\"},{\"id\":\"2\",\"name\":\"two\"}]")));
        wireMockServer.stubFor(get(urlPathEqualTo(ACCOUNTS_PATH))
                .withQueryParam("page", equalTo("2"))
                .willReturn(okJson("[{\"id\":\"3\",\"name\":\"three\"},{\"id\":\"4\",\"name\":\"four\"}]")));
        wireMockServer.stubFor(get(urlPathEqualTo(ACCOUNTS_PATH))
                .withQueryParam("page", equalTo("3"))
                .willReturn(okJson("[{\"id\":\"5\",\"name\":\"five\"}]")));

        var results = search(yamlConnector("""
                - path: accounts
                  responseFormat: JSON_ARRAY
                  emptyFilterSupported: true
                  pagingSupport:
                    pageSize: 2
                    parameters:
                      pageSize:
                        in: query
                        name: size
                      page:
                        in: query
        """), null);

        assertEquals(results.size(), 5);
        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(ACCOUNTS_PATH))
                .withQueryParam("size", equalTo("2"))).size(), 3);
    }

    @Test
    public void offsetTokenIsZeroBasedObjectOffset() {
        wireMockServer.stubFor(get(urlPathEqualTo(ACCOUNTS_PATH))
                .withQueryParam("offset", equalTo("0"))
                .willReturn(okJson("[{\"id\":\"1\",\"name\":\"one\"},{\"id\":\"2\",\"name\":\"two\"}]")));
        wireMockServer.stubFor(get(urlPathEqualTo(ACCOUNTS_PATH))
                .withQueryParam("offset", equalTo("2"))
                .willReturn(okJson("[{\"id\":\"3\",\"name\":\"three\"},{\"id\":\"4\",\"name\":\"four\"}]")));
        wireMockServer.stubFor(get(urlPathEqualTo(ACCOUNTS_PATH))
                .withQueryParam("offset", equalTo("4"))
                .willReturn(okJson("[{\"id\":\"5\",\"name\":\"five\"}]")));

        var results = search(yamlConnector("""
                - path: accounts
                  responseFormat: JSON_ARRAY
                  emptyFilterSupported: true
                  pagingSupport:
                    pageSize: 2
                    parameters:
                      pageSize:
                        in: query
                        name: limit
                      offset:
                        in: query
        """), null);

        assertEquals(results.size(), 5);
        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(ACCOUNTS_PATH))
                .withQueryParam("limit", equalTo("2"))
                .withQueryParam("offset", matching("0|2|4"))).size(), 3);
    }

    @Test
    public void pagingParametersAreAddedToHeaders() {
        wireMockServer.stubFor(get(urlPathEqualTo(ACCOUNTS_PATH))
                .willReturn(okJson("[{\"id\":\"1\",\"name\":\"one\"}]")));

        var results = search(yamlConnector("""
                - path: accounts
                  responseFormat: JSON_ARRAY
                  emptyFilterSupported: true
                  pagingSupport:
                    parameters:
                      pageSize:
                        in: header
                        name: X-Page-Size
                      page:
                        in: header
                        name: X-Page
        """), null);

        assertEquals(results.size(), 1);
        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(ACCOUNTS_PATH))
                .withHeader("X-Page-Size", equalTo("25"))
                .withHeader("X-Page", equalTo("1"))).size(), 1);
    }

    @Test
    public void pagingParametersAreAddedToPostBody() {
        wireMockServer.stubFor(post(urlPathEqualTo(ACCOUNTS_PATH))
                .willReturn(okJson("[{\"id\":\"1\",\"name\":\"one\"}]")));

        var results = search(yamlConnector("""
                - path: accounts
                  method: POST
                  responseFormat: JSON_ARRAY
                  emptyFilterSupported: true
                  pagingSupport:
                    pageSize: 50
                    parameters:
                      pageSize:
                        in: body
                      page:
                        in: body
        """), null);

        assertEquals(results.size(), 1);
        assertEquals(wireMockServer.findAll(postRequestedFor(urlPathEqualTo(ACCOUNTS_PATH))
                .withRequestBody(equalToJson("{\"pageSize\":50,\"page\":1}"))).size(), 1);
    }

    @Test
    public void bodyParameterWithoutPostFails() {
        var connector = yamlConnector("""
                - path: accounts
                  emptyFilterSupported: true
                  pagingSupport:
                    parameters:
                      page:
                        in: body
        """);

        var exception = expectThrows(ConnectorException.class, () -> search(connector, null));
        assertTrue(exception.getMessage(),
                exception.getMessage().contains("requires the endpoint httpOperation to be POST"));
    }

    @Test
    public void unknownPagingLocationFails() {
        var connector = yamlConnector("""
                - path: accounts
                  emptyFilterSupported: true
                  pagingSupport:
                    parameters:
                      page:
                        in: cookie
        """);

        var exception = expectThrows(ConnectorException.class, () -> search(connector, null));
        assertTrue(exception.getMessage(),
                exception.getMessage().contains("cookie"));
    }

    @Test
    public void declarativeAndClosurePagingFail() {
        var script = """
                objectClass("Account") {
                    search {
                        endpoint("accounts") {
                            responseFormat JSON_ARRAY
                            emptyFilterSupported true
                            pagingSupport {
                                request.queryParameter("size", paging.pageSize)
                            }
                            pagingParameter("page", "query")
                        }
                    }
                }
                """;

        var connector = initConnector(script);
        var exception = expectThrows(ConnectorException.class, () -> search(connector, null));
        assertTrue(exception.getMessage(),
                exception.getMessage().contains("declarative paging parameters"));
    }

    @Test
    public void unknownPagingTokenFails() {
        var script = """
                objectClass("Account") {
                    search {
                        endpoint("accounts") {
                            responseFormat JSON_ARRAY
                            emptyFilterSupported true
                            pagingParameter("bogus", "query", "page")
                        }
                    }
                }
                """;

        var connector = initConnector(script);
        var exception = expectThrows(ConnectorException.class, () -> search(connector, null));
        assertTrue(exception.getMessage(),
                exception.getMessage().contains("bogus"));
    }
}
