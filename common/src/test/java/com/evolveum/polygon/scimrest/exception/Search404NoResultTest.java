/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.exception;

import com.evolveum.polygon.conndev.spi.ClassHandlerConnectorBase;
import com.evolveum.polygon.scimrest.support.AbstractCrudConnectorTest;
import com.evolveum.polygon.scimrest.support.YamlOperationsConnector;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.exceptions.ConnectionFailedException;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.testng.annotations.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Bug #12507: an exact-ID lookup through a search endpoint that answers HTTP 404 for a
 * nonexistent object reports a {@code ConfigurationException} instead of an empty result set.
 *
 * <p>The endpoint under test mirrors the OpenProject connector configuration: {@code GET
 * users/{id}} with {@code singleResult()} and an {@code id eq ?} filter. midPoint searches a
 * nonexistent ID (e.g. {@code 2147483647}), the remote answers 404 and the connector turns that
 * into "Search endpoint returned 404 (check the endpoint path)" — a configuration error, even
 * though the endpoint path is correct and only the data are absent.
 */
public class Search404NoResultTest extends AbstractCrudConnectorTest {

    private static final String MISSING_ID = "2147483647";
    private static final String MISSING_ID_PATH = "/accounts/" + MISSING_ID;

    /** An OpenProject-style 404 body; {@code ErrorDetail} picks the {@code error} field. */
    private static final String NOT_FOUND_BODY = "{\"error\":\"The requested resource could not be found\"}";

    /** {@link AbstractCrudConnectorTest#OPERATION_SCRIPT} with the {@code accounts/{id}} endpoint flagged. */
    private static final String OPERATION_SCRIPT_FLAGGED = """
            objectClass("Account") {
                search {
                    endpoint("accounts") { emptyFilterSupported true }
                    endpoint("accounts/{id}") {
                        singleResult()
                        notFoundIsNoResult()
                        supportedFilter(attribute("id").eq().anySingleValue()) {
                            request.pathParameter("id", value)
                        }
                    }
                }
            }
            """;

    /** YAML counterpart of {@link #OPERATION_SCRIPT_FLAGGED}. */
    private static final String YAML_DOC_FLAGGED = """
            objectClasses:
              Account:
                search:
                  endpoints:
                    - path: accounts
                      emptyFilterSupported: true
                    - path: accounts/{id}
                      singleResult: true
                      notFoundIsNoResult: true
                      supportedFilters:
                        - spec: attribute("id").eq().anySingleValue()
                          request: |
                            request.pathParameter("id", value)
            """;

    /**
     * Reproduction (bug #12507): a search for a nonexistent ID routed to the single-result
     * {@code accounts/{id}} endpoint receives a 404, and the connector reports it as a
     * configuration error.
     */
    @Test
    public void searchById404ReportsConfigurationException() {
        wireMockServer.stubFor(get(urlEqualTo(MISSING_ID_PATH))
                .willReturn(aResponse().withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody(NOT_FOUND_BODY)));

        var connector = initConnector(OPERATION_SCRIPT);

        var e = expectThrows(ConfigurationException.class,
                () -> search(connector, FilterBuilder.equalTo(new Uid(MISSING_ID))));
        assertTrue(e.getMessage().contains("Search endpoint returned 404 (check the endpoint path)"),
                "Error should name the search-endpoint 404 problem: " + e.getMessage());
        assertTrue(e.getMessage().contains("HTTP 404"),
                "Error should carry the HTTP status: " + e.getMessage());
        assertTrue(e.getMessage().contains("The requested resource could not be found"),
                "Error should carry the server-provided detail: " + e.getMessage());
        // The request really went to the by-id endpoint — the path is configured correctly,
        // only the object is absent.
        wireMockServer.verify(getRequestedFor(urlEqualTo(MISSING_ID_PATH)));
    }

    /**
     * Regression guard for the unflagged default: an unflagged search endpoint keeps reporting
     * a 404 as a configuration error (the endpoint path may genuinely be misconfigured).
     */
    @Test
    public void unflaggedSearchEndpoint404StillReportsConfigurationException() {
        wireMockServer.stubFor(get(urlEqualTo(MISSING_ID_PATH))
                .willReturn(aResponse().withStatus(404)));

        var connector = initConnector(OPERATION_SCRIPT);

        var e = expectThrows(ConfigurationException.class,
                () -> search(connector, FilterBuilder.equalTo(new Uid(MISSING_ID))));
        assertTrue(e.getMessage().contains("Search endpoint returned 404"),
                "Unflagged endpoints keep the misconfiguration signal: " + e.getMessage());
    }

    /** A flagged endpoint turns the 404 into an empty result set instead of a configuration error. */
    @Test
    public void flaggedSearchEndpoint404ReturnsEmptyResult() {
        wireMockServer.stubFor(get(urlEqualTo(MISSING_ID_PATH))
                .willReturn(aResponse().withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody(NOT_FOUND_BODY)));

        var results = search(initConnector(OPERATION_SCRIPT_FLAGGED),
                FilterBuilder.equalTo(new Uid(MISSING_ID)));

        assertEquals(results.size(), 0, "A 404 on a flagged endpoint is an empty result set, not an error");
        wireMockServer.verify(getRequestedFor(urlEqualTo(MISSING_ID_PATH)));
    }

    /** The same behavior through the YAML front-end. */
    @Test
    public void flaggedSearchEndpointFromYaml404ReturnsEmptyResult() {
        wireMockServer.stubFor(get(urlEqualTo(MISSING_ID_PATH))
                .willReturn(aResponse().withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody(NOT_FOUND_BODY)));

        var results = search(initConnectorFromYaml(YAML_DOC_FLAGGED),
                FilterBuilder.equalTo(new Uid(MISSING_ID)));

        assertEquals(results.size(), 0, "A 404 on a flagged endpoint is an empty result set, not an error");
    }

    /** The flag also applies when the 404 carries no body. */
    @Test
    public void flaggedSearchEndpoint404EmptyBodyReturnsEmptyResult() {
        wireMockServer.stubFor(get(urlEqualTo(MISSING_ID_PATH))
                .willReturn(aResponse().withStatus(404)));

        var results = search(initConnector(OPERATION_SCRIPT_FLAGGED),
                FilterBuilder.equalTo(new Uid(MISSING_ID)));

        assertEquals(results.size(), 0);
    }

    /** The flag only affects 404 — a server error on a flagged endpoint still fails. */
    @Test
    public void flaggedSearchEndpoint500StillThrows() {
        wireMockServer.stubFor(get(urlEqualTo(MISSING_ID_PATH))
                .willReturn(aResponse().withStatus(500)));

        var connector = initConnector(OPERATION_SCRIPT_FLAGGED);

        var e = expectThrows(ConnectionFailedException.class,
                () -> search(connector, FilterBuilder.equalTo(new Uid(MISSING_ID))));
        assertTrue(e.getMessage().contains("HTTP 500"),
                "A 500 stays a server error: " + e.getMessage());
    }

    /** Sanity: a 200 with a body on a flagged endpoint still yields the object. */
    @Test
    public void flaggedSearchEndpoint200StillReturnsObject() {
        wireMockServer.stubFor(get(urlEqualTo(ACCOUNT_BY_ID_PATH))
                .willReturn(okJson("{\"id\":\"123\",\"name\":\"by-id\"}")));

        var results = search(initConnector(OPERATION_SCRIPT_FLAGGED),
                FilterBuilder.equalTo(new Uid("123")));

        assertEquals(results.size(), 1);
        assertEquals(results.getFirst().getUid().getUidValue(), "123");
    }

    private ClassHandlerConnectorBase initConnectorFromYaml(String yamlDocument) {
        var connector = YamlOperationsConnector.fromStrings(yamlDocument);
        connector.init(new SimpleConfig(wireMockServer.port()));
        return connector;
    }
}
