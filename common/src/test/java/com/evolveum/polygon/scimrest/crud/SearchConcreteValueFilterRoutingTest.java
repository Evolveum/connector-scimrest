/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.crud;

import com.evolveum.polygon.conndev.spi.ClassHandlerConnectorBase;
import com.evolveum.polygon.scimrest.support.AbstractCrudConnectorTest;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.testng.Assert;
import org.testng.annotations.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Verifies the closure-less {@code supportedFilter} (a concrete-value filter): the endpoint
 * itself is the filter (e.g. {@code accounts/disabled} already returns only the disabled
 * accounts), so a search whose filter matches the declared specification is routed to that
 * endpoint with the request left unmodified, and a non-matching filter is not routed there.
 */
public class SearchConcreteValueFilterRoutingTest extends AbstractCrudConnectorTest {

    private static final String SCHEMA_SCRIPT = """
            objectClass("Account") {
                attribute("id") { jsonType "string" }
                attribute("name") { jsonType "string" }
                attribute("enabled") { jsonType "boolean" }
            }
            """;

    private static final String OPERATION_SCRIPT = """
            objectClass("Account") {
                search {
                    endpoint("accounts") {
                        responseFormat JSON_ARRAY
                        emptyFilterSupported true
                    }
                    endpoint("accounts/disabled") {
                        responseFormat JSON_ARRAY
                        supportedFilter(attribute("enabled").eq(false))
                        pagingSupport {
                            request.queryParameter("limit", paging.pageSize)
                                   .queryParameter("offset", paging.pageOffset)
                        }
                    }
                }
            }
            """;

    private static final String DISABLED_PATH = "/accounts/disabled";

    private static final Filter EQ_FALSE_FILTER =
            FilterBuilder.equalTo(AttributeBuilder.build("enabled", false));

    private static final Filter EQ_TRUE_FILTER =
            FilterBuilder.equalTo(AttributeBuilder.build("enabled", true));

    private ClassHandlerConnectorBase initConnector() {
        return initConnector(SCHEMA_SCRIPT, CONNID_SCHEMA_SCRIPT, OPERATION_SCRIPT);
    }

    private void stubDisabledAccounts() {
        wireMockServer.stubFor(get(urlPathEqualTo(DISABLED_PATH))
                .willReturn(okJson("[{\"id\":\"1\",\"name\":\"alice\",\"enabled\":false}]")));
    }

    @Test
    public void concreteValueFilterRoutesToDedicatedEndpoint() {
        stubDisabledAccounts();

        var results = search(initConnector(), EQ_FALSE_FILTER);

        assertEquals(results.size(), 1);
        assertEquals(results.getFirst().getName().getNameValue(), "alice");
        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(DISABLED_PATH))).size(), 1);
        // The generic endpoint must not be consulted at all.
        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(ACCOUNTS_PATH))).size(), 0);
    }

    @Test
    public void pagingIsAppliedOnTheRoutedEndpoint() {
        stubDisabledAccounts();

        var options = buildOptions(buildPageEntries(42, 1));
        search(initConnector(), EQ_FALSE_FILTER, options);

        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(DISABLED_PATH))
                .withQueryParam("limit", equalTo("42"))).size(), 1);
    }

    @Test
    public void unmatchedConcreteValueIsNotRoutedToTheEndpoint() {
        stubDisabledAccounts();

        var exception = Assert.expectThrows(Exception.class, () -> search(initConnector(), EQ_TRUE_FILTER));

        assertTrue(exception instanceof ConnectorException);
        assertTrue(exception.getCause() instanceof IllegalArgumentException,
                "Expected the dispatcher's Unsupported filter rejection, got: " + exception.getCause());
        assertTrue(exception.getCause().getMessage().contains("Unsupported filter"));
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 0);
    }
}
