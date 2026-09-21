/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.crud;

import com.evolveum.polygon.scimrest.support.AbstractCrudConnectorTest;
import org.testng.annotations.Ignore;
import org.testng.annotations.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.assertEquals;

/**
 * Verifies the {@code pagingSupport} search directive: the closure receives the request
 * and paging info and is responsible for mapping page size/offset onto the request.
 */
public class SearchPagingSupportTest extends AbstractCrudConnectorTest {

    private static final String SCRIPT = """
            objectClass("Account") {
                search {
                    endpoint("accounts") {
                        responseFormat JSON_ARRAY
                        emptyFilterSupported true
                        pagingSupport {
                            request.queryParameter("pageSize", paging.pageSize)
                                   .queryParameter("page", paging.pageOffset)
                        }
                    }
                }
            }
            """;

    private static final String SCRIPT_MPS_TEMPLATE = """
        objectClass("Account") {
            search {
                endpoint("accounts") {
                    responseFormat JSON_ARRAY
                    emptyFilterSupported true
                    %s
                    pagingSupport {
                        request.queryParameter("pageSize", paging.pageSize)
                               .queryParameter("page", paging.pageOffset)
                    }
                }
            }
        }
        """;

    private static final String SCRIPT_NO_PAGING = """
        objectClass("Account") {
            search {
                endpoint("accounts") {
                    responseFormat JSON_ARRAY
                    emptyFilterSupported true
                }
            }
        }
        """;

    @Test
    public void pagingParametersAreAddedToRequest() {
        wireMockServer.stubFor(get(urlPathEqualTo(ACCOUNTS_PATH))
                .willReturn(okJson("""
                        [{"id":"1","name":"first"},{"id":"2","name":"second"}]
                        """)));

        var results = search(initConnector(SCRIPT), null,
                buildOptions(buildPageEntries(25,  1)));

        assertEquals(results.size(), 2);
        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(ACCOUNTS_PATH))
                .withQueryParam("pageSize", equalTo("25"))
                .withQueryParam("page", equalTo("1"))).size(), 1);
    }

    @Ignore
    @Deprecated
    @Test
    public void fullPageTriggersFetchOfNextPage() {
        var fullPage = new StringBuilder("[");
        for (int i = 1; i <= 25; i++) {
            fullPage.append("{\"id\":\"").append(i).append("\",\"name\":\"user-").append(i).append("\"},");
        }
        fullPage.setLength(fullPage.length() - 1);
        fullPage.append("]");

        wireMockServer.stubFor(get(urlPathEqualTo(ACCOUNTS_PATH))
                .withQueryParam("page", equalTo("1"))
                .willReturn(okJson(fullPage.toString())));
        wireMockServer.stubFor(get(urlPathEqualTo(ACCOUNTS_PATH))
                .withQueryParam("page", equalTo("2"))
                .willReturn(okJson("[{\"id\":\"26\",\"name\":\"user-26\"}]")));

        var results = search(initConnector(SCRIPT), null,
                buildOptions(buildPageEntries(25,  1)));

        assertEquals(results.size(), 25);
        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(ACCOUNTS_PATH))).size(), 2);
    }

    @Test
    public void pagingParametersMaxPageSizeSmallerThanRequested() {

        wireMockServer.stubFor(get(urlPathEqualTo(ACCOUNTS_PATH))
                .withQueryParam("pageSize", equalTo("15"))
                .withQueryParam("page", equalTo("1"))
                .willReturn(okJson(buildUserDataArray(1, 15))));
        wireMockServer.stubFor(get(urlPathEqualTo(ACCOUNTS_PATH))
                .withQueryParam("pageSize", equalTo("15"))
                .withQueryParam("page", equalTo("2"))
                .willReturn(okJson(buildUserDataArray(16, 25))));

        var results = search(initConnector(scriptMPS(15)), null,
                buildOptions(buildPageEntries(25,  1)));

        assertEquals(results.size(), 25);
        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(ACCOUNTS_PATH))
                .withQueryParam("pageSize", equalTo("15"))
                .withQueryParam("page", equalTo("1"))).size(), 1);
        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(ACCOUNTS_PATH))
                .withQueryParam("pageSize", equalTo("15"))
                .withQueryParam("page", equalTo("2"))).size(), 1);
    }

    @Test
    public void pagingParametersMaxPageSizeGreaterThanRequested() {

        wireMockServer.stubFor(get(urlPathEqualTo(ACCOUNTS_PATH))
                .withQueryParam("pageSize", equalTo("25"))
                .withQueryParam("page", equalTo("1"))
                .willReturn(okJson(buildUserDataArray(1, 25))));

        var results = search(initConnector(scriptMPS(30)), null,
                buildOptions(buildPageEntries(25,  1)));

        assertEquals(results.size(), 25);
        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(ACCOUNTS_PATH))
                .withQueryParam("pageSize", equalTo("25"))
                .withQueryParam("page", equalTo("1"))).size(), 1);
    }

    @Test
    public void requestWithNoPagingDefinitionEmptyOperationOptions() {

        wireMockServer.stubFor(get(urlPathEqualTo(ACCOUNTS_PATH))
                .willReturn(okJson(buildUserDataArray(1, 25))));

        var results = search(initConnector(SCRIPT_NO_PAGING), null,buildOptions());
        assertEquals(results.size(), 25);
    }

    @Test
    public void requestWithNoPagingDefinitionWithOperationOptions() {

        wireMockServer.stubFor(get(urlPathEqualTo(ACCOUNTS_PATH))
                .willReturn(okJson(buildUserDataArray(1, 25))));

        var results = search(initConnector(SCRIPT_NO_PAGING), null,
                buildOptions(buildPageEntries()));
        assertEquals(results.size(), 20);
    }

    private static String buildUserDataArray(int fromId, int toId) {
        var sb = new StringBuilder("[");
        for (int i = fromId; i <= toId; i++) {
            sb.append("{\"id\":\"").append(i).append("\",\"name\":\"user-").append(i).append("\"}");
            if (i < toId) {
                sb.append(',');
            }
        }
        return sb.append(']').toString();
    }

    private static String scriptMPS(Integer maxPageSize) {
        String maxPageSizeLine = maxPageSize != null ? "maxPageSize " + maxPageSize : "";
        return SCRIPT_MPS_TEMPLATE.formatted(maxPageSizeLine);
    }
}
