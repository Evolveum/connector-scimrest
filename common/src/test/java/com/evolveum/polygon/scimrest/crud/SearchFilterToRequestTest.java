/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.crud;

import com.evolveum.polygon.scimrest.support.AbstractCrudConnectorTest;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.testng.annotations.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.assertEquals;

/**
 * Verifies the {@code supportedFilter} closure (filter-to-request mapping): the closure
 * receives the filter value and maps it onto the request, e.g. as a query parameter.
 */
public class SearchFilterToRequestTest extends AbstractCrudConnectorTest {

    private static final String SCRIPT = """
            objectClass("Account") {
                search {
                    endpoint("accounts") {
                        responseFormat JSON_ARRAY
                        supportedFilter(attribute("name").contains().anySingleValue()) {
                            request.queryParameter("nameLike", value)
                        }
                    }
                }
            }
            """;

    @Test
    public void containsFilterIsMappedToQueryParameter() {
        wireMockServer.stubFor(get(urlPathEqualTo(ACCOUNTS_PATH))
                .willReturn(okJson("[{\"id\":\"7\",\"name\":\"alice\"}]")));

        var filter = FilterBuilder.contains(AttributeBuilder.build(Name.NAME, "ali"));
        var results = search(initConnector(SCRIPT), filter);

        assertEquals(results.size(), 1);
        assertEquals(results.get(0).getName().getNameValue(), "alice");
        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(ACCOUNTS_PATH))
                .withQueryParam("nameLike", equalTo("ali"))).size(), 1);
    }
}
