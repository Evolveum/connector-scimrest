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
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.AttributeDeltaBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.Uid;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.assertEquals;

/**
 * YAML form of the create and update operations, mirroring the Groovy DSL behaviour. Documents
 * follow the wizard file-per-operation convention: the update flow reads the object first, so its
 * connector loads the by-id search document alongside the update document.
 */
public class YamlCreateUpdateTest extends AbstractCrudConnectorTest {

    /** YAML counterpart of {@code Account.search.id.op.groovy} — updates read the object first. */
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

    private static final String CREATE_YAML = """
            objectClasses:
              Account:
                create:
                  endpoints:
                    - method: POST
                      path: accounts
            """;

    private static final String PATCH_UPDATE_YAML = """
            objectClasses:
              Account:
                update:
                  endpoints:
                    - method: PATCH
                      path: accounts/{id}
                      request:
                        contentType: application/json
            """;

    /** The Groovy-DSL constant spelling — accepted by the YAML front-end as well (bug 12410). */
    private static final String CREATE_WITH_CONTENT_TYPE_CONSTANT_YAML = """
            objectClasses:
              Account:
                create:
                  endpoints:
                    - method: POST
                      path: accounts
                      request:
                        contentType: APPLICATION_JSON
            """;

    private static final String PATCH_UPDATE_WITH_CONTENT_TYPE_CONSTANT_YAML = """
            objectClasses:
              Account:
                update:
                  endpoints:
                    - method: PATCH
                      path: accounts/{id}
                      request:
                        contentType: APPLICATION_JSON
            """;

    /**
     * Mirrors the codegen convention (e.g. {@code User.op.yaml}): a JSON endpoint plus a
     * dedicated no-body endpoint declared with {@code body: EMPTY} (bug 12411).
     */
    private static final String EMPTY_BODY_UPDATE_YAML = """
            objectClasses:
              Account:
                update:
                  endpoints:
                    - method: PUT
                      path: accounts/{id}
                      request:
                        contentType: APPLICATION_JSON
                      supportedAttributes:
                        - displayName
                    - method: POST
                      path: accounts/{id}/activate
                      request:
                        body: EMPTY
                      supportedAttributes:
                        - name: status
                          value: active
            """;

    // Schema with the extra attributes the supported-attributes routing needs.
    private static final String SCHEMA_WITH_EXTRA = """
            objectClass("Account") {
                attribute("id") { jsonType "string" }
                attribute("name") { jsonType "string" }
                attribute("displayName") { jsonType "string" }
                attribute("status") { jsonType "string" }
            }
            """;

    private static final String SUPPORTED_UPDATE_YAML = """
            objectClasses:
              Account:
                update:
                  endpoints:
                    - method: PUT
                      path: accounts/{id}
                      request:
                        contentType: application/json
                      supportedAttributes:
                        - displayName
                    - method: POST
                      path: accounts/{id}/activate
                      supportedAttributes:
                        - status
            """;

    @Test
    public void createFromYaml() {
        stubCreateAccount();

        createAccount(initYaml(CREATE_YAML));

        assertEquals(wireMockServer.findAll(postRequestedFor(urlEqualTo(ACCOUNTS_PATH))).size(), 1);
    }

    @Test
    public void patchUpdateFromYaml() {
        wireMockServer.stubFor(get(urlEqualTo(ACCOUNT_BY_ID_PATH))
                .willReturn(okJson("{\"id\":\"123\",\"name\":\"old-name\"}")));
        wireMockServer.stubFor(patch(urlPathMatching(ACCOUNTS_PATTERN))
                .willReturn(okJson("{\"id\":\"123\",\"name\":\"new-name\"}")));

        initYaml(SEARCH_BY_ID_YAML, PATCH_UPDATE_YAML).updateDelta(new ObjectClass("Account"),
                new Uid("123"),
                Set.of(AttributeDeltaBuilder.build(Name.NAME, List.of("new-name"))),
                new OperationOptionsBuilder().build());

        assertEquals(wireMockServer.findAll(patchRequestedFor(urlEqualTo(ACCOUNT_BY_ID_PATH))
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(matchingJsonPath("$.name", equalTo("new-name")))).size(), 1);
    }

    @Test
    public void createFromYamlWithContentTypeConstant() {
        stubCreateAccount();

        var connector = initYaml(CREATE_WITH_CONTENT_TYPE_CONSTANT_YAML);
        connector.create(new ObjectClass("Account"),
                Set.of(new Name("test")),
                new OperationOptionsBuilder().build());

        assertEquals(wireMockServer.findAll(postRequestedFor(urlEqualTo(ACCOUNTS_PATH))
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(matchingJsonPath("$.name", equalTo("test")))).size(), 1);
    }

    @Test
    public void patchUpdateFromYamlWithContentTypeConstant() {
        wireMockServer.stubFor(get(urlEqualTo(ACCOUNT_BY_ID_PATH))
                .willReturn(okJson("{\"id\":\"123\",\"name\":\"old-name\"}")));
        wireMockServer.stubFor(patch(urlPathMatching(ACCOUNTS_PATTERN))
                .willReturn(okJson("{\"id\":\"123\",\"name\":\"new-name\"}")));

        initYaml(SEARCH_BY_ID_YAML, PATCH_UPDATE_WITH_CONTENT_TYPE_CONSTANT_YAML).updateDelta(
                new ObjectClass("Account"),
                new Uid("123"),
                Set.of(AttributeDeltaBuilder.build(Name.NAME, List.of("new-name"))),
                new OperationOptionsBuilder().build());

        assertEquals(wireMockServer.findAll(patchRequestedFor(urlEqualTo(ACCOUNT_BY_ID_PATH))
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(matchingJsonPath("$.name", equalTo("new-name")))).size(), 1);
    }

    @Test
    public void emptyBodyEndpointFromYaml() {
        stubReadById();
        wireMockServer.stubFor(post(urlEqualTo(ACCOUNT_BY_ID_PATH + "/activate"))
                .willReturn(okJson("{\"id\":\"123\",\"name\":\"acc\",\"status\":\"active\"}")));

        var connector = YamlOperationsConnector.fromStrings(SEARCH_BY_ID_YAML, EMPTY_BODY_UPDATE_YAML)
                .withSchema(SCHEMA_WITH_EXTRA, CONNID_SCHEMA_SCRIPT);
        connector.init(new SimpleConfig(wireMockServer.port()));

        updateDelta(connector, "status", "active");

        var activates = wireMockServer.findAll(postRequestedFor(urlEqualTo(ACCOUNT_BY_ID_PATH + "/activate")));
        assertEquals(activates.size(), 1);
        assertEquals(activates.getFirst().getBodyAsString(), "");
        assertEquals(wireMockServer.findAll(putRequestedFor(urlPathMatching(ACCOUNTS_PATTERN))).size(), 0);
    }

    @Test(expectedExceptions = ConfigurationException.class)
    public void groovyBlockBodyIsRejected() {
        // Custom request bodies are not supported yet — only the EMPTY sentinel and the
        // default serialization (the Groovy DSL's `body { ... }` is rejected the same way).
        // Handlers are built lazily on the first operation.
        var connector = initYaml("""
                objectClasses:
                  Account:
                    update:
                      endpoints:
                        - method: PATCH
                          path: accounts/{id}
                          request:
                            body: |
                              request.body(someBytes)
                """);
        updateDelta(connector, "name", "other");
    }

    @Test
    public void statusDeltaGoesToActivateEndpoint() {
        stubReadById();
        wireMockServer.stubFor(post(urlEqualTo(ACCOUNT_BY_ID_PATH + "/activate"))
                .willReturn(okJson("{\"id\":\"123\",\"name\":\"acc\",\"status\":\"active\"}")));

        updateDelta(initExtendedYaml(), "status", "active");

        assertEquals(wireMockServer.findAll(postRequestedFor(urlEqualTo(ACCOUNT_BY_ID_PATH + "/activate"))).size(), 1);
        assertEquals(wireMockServer.findAll(putRequestedFor(urlPathMatching(ACCOUNTS_PATTERN))).size(), 0);
    }

    @Test
    public void displayNameDeltaGoesToPutEndpoint() {
        stubReadById();
        wireMockServer.stubFor(put(urlEqualTo(ACCOUNT_BY_ID_PATH))
                .willReturn(okJson("{\"id\":\"123\",\"name\":\"acc\",\"displayName\":\"Renamed\"}")));

        updateDelta(initExtendedYaml(), "displayName", "Renamed");

        assertEquals(wireMockServer.findAll(putRequestedFor(urlEqualTo(ACCOUNT_BY_ID_PATH))
                .withRequestBody(matchingJsonPath("$.displayName", equalTo("Renamed")))).size(), 1);
        assertEquals(wireMockServer.findAll(postRequestedFor(urlPathMatching(ACCOUNTS_PATTERN))).size(), 0);
    }

    @Test(expectedExceptions = ConnectorException.class)
    public void unsupportedDeltaIsRejected() {
        stubReadById();

        updateDelta(initExtendedYaml(), "name", "other");
    }

    // ----------------------------------------------------------------------------------------------

    private void stubReadById() {
        wireMockServer.stubFor(get(urlEqualTo(ACCOUNT_BY_ID_PATH))
                .willReturn(okJson("{\"id\":\"123\",\"name\":\"acc\",\"displayName\":\"Acc\",\"status\":\"inactive\"}")));
    }

    private void updateDelta(ClassHandlerConnectorBase connector, String attribute, String value) {
        connector.updateDelta(new ObjectClass("Account"),
                new Uid("123"),
                Set.of(AttributeDeltaBuilder.build(attribute, List.of(value), null)),
                new OperationOptionsBuilder().build());
    }

    private ClassHandlerConnectorBase initYaml(String... yamlDocuments) {
        var connector = YamlOperationsConnector.fromStrings(yamlDocuments);
        connector.init(new SimpleConfig(wireMockServer.port()));
        return connector;
    }

    private ClassHandlerConnectorBase initExtendedYaml() {
        var connector = YamlOperationsConnector.fromStrings(SEARCH_BY_ID_YAML, SUPPORTED_UPDATE_YAML)
                .withSchema(SCHEMA_WITH_EXTRA, CONNID_SCHEMA_SCRIPT);
        connector.init(new SimpleConfig(wireMockServer.port()));
        return connector;
    }
}
