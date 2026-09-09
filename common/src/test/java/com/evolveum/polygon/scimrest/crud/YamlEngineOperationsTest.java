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
import org.identityconnectors.framework.common.objects.AttributeDeltaBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.assertEquals;

/**
 * The extended (plural) operations envelope is driven by the location-aware engine onto the live
 * builders. This proves the end-to-end path: {@code objectClasses -> create -> endpoints} binds onto
 * the real {@code RestCreateOperationBuilderImpl}, and the resulting operation executes against the
 * wire exactly like its Groovy-DSL counterpart.
 */
public class YamlEngineOperationsTest extends AbstractCrudConnectorTest {

    private static final String CREATE_YAML = """
            objectClasses:
              Account:
                create:
                  endpoints:
                    - method: POST
                      path: accounts
            """;

    private static final String DELETE_YAML = """
            objectClasses:
              Account:
                delete:
                  endpoints:
                    - method: DELETE
                      path: accounts/{id}
            """;

    /** Read-by-id search — the update flow reads the object first. */
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

    private static final String UPDATE_YAML = """
            objectClasses:
              Account:
                update:
                  endpoints:
                    - method: PUT
                      path: accounts/{id}
                      request:
                        contentType: application/json
                      supportedAttributes:
                        - name
                    - method: POST
                      path: accounts/{id}/activate
                      supportedAttributes:
                        - status
            """;

    /** Plural-form search: a list-all endpoint (with object extractor) and a by-id single-result endpoint. */
    private static final String SEARCH_ENGINE_YAML = """
            objectClasses:
              Account:
                search:
                  endpoints:
                    - path: accounts
                      emptyFilterSupported: true
                      objectExtractor: |
                        response.body().get("_embedded").get("elements")
                    - path: accounts/{id}
                      singleResult: true
                      supportedFilters:
                        - spec: |
                            attribute("id").eq().anySingleValue()
                          request: |
                            request.pathParameter("id", value)
            """;

    @Test
    public void searchListsAccountsFromYamlEngine() {
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

        var results = search(initYaml(SEARCH_ENGINE_YAML), null);

        assertEquals(results.size(), 2);
        var uids = results.stream().map(o -> o.getUid().getUidValue()).collect(Collectors.toSet());
        assertEquals(uids, Set.of("1", "2"));
        assertEquals(wireMockServer.findAll(getRequestedFor(urlEqualTo(ACCOUNTS_PATH))).size(), 1);
    }

    @Test
    public void searchByIdFromYamlEngine() {
        wireMockServer.stubFor(get(urlEqualTo(ACCOUNT_BY_ID_PATH))
                .willReturn(okJson("""
                        {"id": "123", "name": "by-id"}
                        """)));

        var results = search(initYaml(SEARCH_ENGINE_YAML), FilterBuilder.equalTo(new Uid("123")));

        assertEquals(results.size(), 1);
        assertEquals(results.getFirst().getUid().getUidValue(), "123");
        assertEquals(results.getFirst().getName().getNameValue(), "by-id");
        wireMockServer.verify(getRequestedFor(urlEqualTo(ACCOUNT_BY_ID_PATH)));
    }

    @Test
    public void createFromYamlEngine() {
        stubCreateAccount();

        createAccount(initYaml(CREATE_YAML));

        assertEquals(wireMockServer.findAll(postRequestedFor(urlEqualTo(ACCOUNTS_PATH))).size(), 1);
    }

    @Test
    public void updateFromYamlEngine() {
        wireMockServer.stubFor(get(urlEqualTo(ACCOUNT_BY_ID_PATH))
                .willReturn(okJson("{\"id\":\"123\",\"name\":\"old-name\"}")));
        wireMockServer.stubFor(put(urlPathMatching(ACCOUNTS_PATTERN))
                .willReturn(okJson("{\"id\":\"123\",\"name\":\"new-name\"}")));

        var connector = initExtendedYaml();
        connector.updateDelta(new ObjectClass("Account"),
                new Uid("123"),
                Set.of(AttributeDeltaBuilder.build(Name.NAME, List.of("new-name"))),
                new OperationOptionsBuilder().build());

        assertEquals(wireMockServer.findAll(putRequestedFor(urlPathMatching(ACCOUNTS_PATTERN))).size(), 1);
    }

    @Test
    public void statusTransitionRoutesToActivateEndpoint() {
        wireMockServer.stubFor(get(urlEqualTo(ACCOUNT_BY_ID_PATH))
                .willReturn(okJson("{\"id\":\"123\",\"name\":\"acc\",\"status\":\"active\"}")));
        wireMockServer.stubFor(post(urlEqualTo(ACCOUNT_BY_ID_PATH + "/activate"))
                .willReturn(okJson("{\"id\":\"123\",\"name\":\"acc\",\"status\":\"locked\"}")));

        var connector = initExtendedYaml();
        connector.updateDelta(new ObjectClass("Account"),
                new Uid("123"),
                Set.of(AttributeDeltaBuilder.build("status", List.of("locked"), null)),
                new OperationOptionsBuilder().build());

        assertEquals(wireMockServer.findAll(postRequestedFor(urlEqualTo(ACCOUNT_BY_ID_PATH + "/activate"))).size(), 1);
        assertEquals(wireMockServer.findAll(putRequestedFor(urlPathMatching(ACCOUNTS_PATTERN))).size(), 0);
    }

    @Test
    public void deleteFromYamlEngine() {
        wireMockServer.stubFor(delete(urlEqualTo(ACCOUNT_BY_ID_PATH))
                .willReturn(aResponse().withStatus(204)));

        var connector = initYaml(DELETE_YAML);
        connector.delete(new ObjectClass("Account"), new Uid("123"), new OperationOptionsBuilder().build());

        assertEquals(wireMockServer.findAll(deleteRequestedFor(urlEqualTo(ACCOUNT_BY_ID_PATH))).size(), 1);
    }

    private ClassHandlerConnectorBase initExtendedYaml() {
        // The extended schema adds the 'status'/'name' attributes the supported-attributes routing needs.
        var schema = """
                objectClass("Account") {
                    attribute("id") { jsonType "string" }
                    attribute("name") { jsonType "string" }
                    attribute("status") { jsonType "string" }
                }
                """;
        var connector = YamlOperationsConnector.fromStrings(SEARCH_BY_ID_YAML, UPDATE_YAML).withSchema(schema, CONNID_SCHEMA_SCRIPT);
        connector.init(new SimpleConfig(wireMockServer.port()));
        return connector;
    }

    private ClassHandlerConnectorBase initYaml(String... yamlDocuments) {
        var connector = YamlOperationsConnector.fromStrings(yamlDocuments);
        connector.init(new SimpleConfig(wireMockServer.port()));
        return connector;
    }
}
