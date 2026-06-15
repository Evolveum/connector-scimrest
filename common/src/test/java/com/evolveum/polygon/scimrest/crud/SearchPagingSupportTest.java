/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.crud;

import com.evolveum.polygon.scimrest.support.AbstractCrudConnectorTest;
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

    @Test
    public void pagingParametersAreAddedToRequest() {
        wireMockServer.stubFor(get(urlPathEqualTo(ACCOUNTS_PATH))
                .willReturn(okJson("""
                        [{"id":"1","name":"first"},{"id":"2","name":"second"}]
                        """)));

        var results = search(initConnector(SCRIPT), null);

        assertEquals(results.size(), 2);
        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(ACCOUNTS_PATH))
                .withQueryParam("pageSize", equalTo("25"))
                .withQueryParam("page", equalTo("1"))).size(), 1);
    }

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

        var results = search(initConnector(SCRIPT), null);

        assertEquals(results.size(), 26);
        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(ACCOUNTS_PATH))).size(), 2);
    }
}
