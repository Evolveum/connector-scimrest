/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.crud;

import com.evolveum.polygon.scimrest.ClassHandlerConnectorBase;
import com.evolveum.polygon.scimrest.support.AbstractCrudConnectorTest;
import com.evolveum.polygon.scimrest.support.YamlOperationsConnector;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.testng.annotations.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.expectThrows;
import static org.testng.AssertJUnit.assertTrue;

/**
 * Exercises the <b>YAML</b> form of the search operation against WireMock, mirroring the Groovy DSL
 * behaviour. The structure is declarative; imperative leaves ({@code objectExtractor}, the
 * {@code supportedFilter} spec and its request mapping) are Groovy block scalars bridged onto the same
 * builder hooks the Groovy DSL uses. Following the wizard file-per-operation convention, the "list
 * all" and "by id" variants are two documents that merge into one search operation, exactly like two
 * Groovy {@code search { ... }} contributions.
 *
 * @see SearchObjectExtractorTest the Groovy equivalent of the list/extractor case
 */
public class YamlSearchOperationTest extends AbstractCrudConnectorTest {

    /** YAML counterpart of {@code Account.search.all.op.groovy}. */
    private static final String SEARCH_ALL_YAML = """
            objectClass: Account
            search:
              endpoints:
                - path: accounts
                  emptyFilterSupported: true
                  objectExtractor: |
                    response.body().get("_embedded").get("elements")
            """;

    /** YAML counterpart of {@code Account.search.id.op.groovy}. */
    private static final String SEARCH_BY_ID_YAML = """
            objectClass: Account
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
    public void searchListsAccountsFromYaml() {
        wireMockServer.stubFor(get(urlEqualTo(ACCOUNTS_PATH))
                .willReturn(okJson("""
                        {
                          "_embedded": {
                            "elements": [
                              {"id": "1", "name": "first"},
                              {"id": "2", "name": "second"}
                            ]
                          },
                          "total": 2
                        }
                        """)));

        var results = search(initConnectorFromYaml(), null);

        assertEquals(results.size(), 2);
        var uids = results.stream().map(o -> o.getUid().getUidValue()).collect(Collectors.toSet());
        assertEquals(uids, Set.of("1", "2"));
        var names = results.stream().map(o -> o.getName().getNameValue()).collect(Collectors.toSet());
        assertEquals(names, Set.of("first", "second"));
    }

    @Test
    public void searchByIdFromYaml() {
        wireMockServer.stubFor(get(urlEqualTo(ACCOUNT_BY_ID_PATH))
                .willReturn(okJson("""
                        {"id": "123", "name": "by-id"}
                        """)));

        var results = search(initConnectorFromYaml(), FilterBuilder.equalTo(new Uid("123")));

        assertEquals(results.size(), 1);
        assertEquals(results.get(0).getUid().getUidValue(), "123");
        assertEquals(results.get(0).getName().getNameValue(), "by-id");
        wireMockServer.verify(getRequestedFor(urlEqualTo(ACCOUNT_BY_ID_PATH)));
    }

    /**
     * The manifest may keep referencing the conventional Groovy file name; when the bundle carries
     * the operation as YAML instead, the loader falls back to it and the operation still executes.
     * ({@code /yaml/Account.search.all.op.groovy} does not exist, {@code .yaml} does.)
     */
    @Test
    public void searchWorksThroughGroovyToYamlResourceFallback() {
        wireMockServer.stubFor(get(urlEqualTo(ACCOUNTS_PATH))
                .willReturn(okJson("""
                        {"_embedded": {"elements": [{"id": "1", "name": "first"}]}}
                        """)));

        var connector = YamlOperationsConnector.fromResources("/yaml/Account.search.all.op.groovy");
        connector.init(new SimpleConfig(wireMockServer.port()));

        var results = search(connector, null);

        assertEquals(results.size(), 1);
        assertEquals(results.get(0).getUid().getUidValue(), "1");
    }

    /** A referenced script with neither the Groovy file nor a YAML fallback fails clearly at init. */
    @Test
    public void missingScriptWithoutYamlFallbackIsRejected() {
        var connector = YamlOperationsConnector.fromResources("/yaml/Nonexistent.search.all.op.groovy");
        connector.init(new SimpleConfig(wireMockServer.port()));

        var exception = expectThrows(ConnectorException.class, () -> search(connector, null));
        assertTrue(exception.getMessage().contains("YAML fallback"));
    }

    private ClassHandlerConnectorBase initConnectorFromYaml() {
        var connector = YamlOperationsConnector.fromStrings(SEARCH_ALL_YAML, SEARCH_BY_ID_YAML);
        connector.init(new SimpleConfig(wireMockServer.port()));
        return connector;
    }
}
