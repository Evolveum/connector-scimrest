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
public class SearchRequestSupportTest extends AbstractCrudConnectorTest {

    private static final String SCRIPT = """
            objectClass("Account") {
                search {
                    endpoint("accounts") {
                        emptyFilterSupported true
                        responseFormat JSON_ARRAY
                        request {
                            accept APPLICATION_JSON
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

        search(initConnector(SCRIPT), null);
        verify(getRequestedFor(urlEqualTo(ACCOUNTS_PATH))
                .withHeader("Accept", equalTo("application/json")));
    }
}
