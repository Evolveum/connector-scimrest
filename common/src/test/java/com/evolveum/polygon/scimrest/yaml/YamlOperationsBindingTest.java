/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.yaml;

import com.evolveum.polygon.conndev.spi.ClassHandlerConnectorBase;
import com.evolveum.polygon.scimrest.support.AbstractCrudConnectorTest;
import com.evolveum.polygon.scimrest.support.YamlOperationsConnector;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.expectThrows;
import static org.testng.Assert.assertTrue;

/**
 * End-to-end checks of the two new operations-document bindings: the {@code objectsPath:}
 * keyword of the search endpoints (the unambiguous path form, as opposed to the
 * {@code objectExtractor:} scalar which is Groovy code) and the {@code supportedAttributes:}
 * block on CREATE endpoints (previously bound on update endpoints only).
 */
public class YamlOperationsBindingTest extends AbstractCrudConnectorTest {

    private static final String EMBEDDED_BODY =
            "{\"_embedded\":{\"accounts\":[{\"id\":\"123\",\"name\":\"embedded\"}]}}";

    private static final String CREATE_YAML = """
            objectClasses:
              Account:
                create:
                  endpoints:
                    - path: accounts
                      supportedAttributes:
                        - id
                        - name
            """;

    private static final String CREATE_GROOVY = """
            objectClass("Account") {
                create {
                    endpoint("accounts") {
                        supportedAttributes "id", "name"
                    }
                }
            }
            """;

    private static final String SEARCH_YAML_OBJECTS_PATH_SCALAR = """
            objectClasses:
              Account:
                search:
                  endpoints:
                    - path: accounts
                      emptyFilterSupported: true
                      objectsPath: $._embedded.accounts
            """;

    private static final String SEARCH_YAML_OBJECTS_PATH_MAPPING = """
            objectClasses:
              Account:
                search:
                  endpoints:
                    - path: accounts
                      emptyFilterSupported: true
                      objectsPath:
                        type: JSON_POINTER
                        value: /_embedded/accounts
            """;

    @Test
    public void objectsPathScalarExtractsTheNestedArray() {
        stubEmbeddedAccounts();

        var results = searchAll(initConnectorWithYamlSearch(SEARCH_YAML_OBJECTS_PATH_SCALAR));

        assertEquals(results.size(), 1);
        assertEquals(results.get(0).getUid().getUidValue(), "123");
    }

    @Test
    public void objectsPathMappingFormExtractsTheNestedArray() {
        stubEmbeddedAccounts();

        var results = searchAll(initConnectorWithYamlSearch(SEARCH_YAML_OBJECTS_PATH_MAPPING));

        assertEquals(results.size(), 1);
        assertEquals(results.get(0).getUid().getUidValue(), "123");
    }

    @Test
    public void objectsPathRejectsAnUnknownFormatName() {
        var connector = YamlOperationsConnector.fromStrings("""
                objectClasses:
                  Account:
                    search:
                      endpoints:
                        - path: accounts
                          emptyFilterSupported: true
                          objectsPath:
                            type: BOGUS
                            value: /_embedded/accounts
                """);

        // the operation handlers (and with them the YAML binding) are built lazily on the
        // first operation — a plain init() never sees the document
        var failure = expectThrows(Exception.class, () -> {
            connector.init(new SimpleConfig(wireMockServer.port()));
            search(connector, null);
        });
        assertTrue(messageChain(failure).contains("BOGUS"),
                "expected the unknown format name in the failure, got: " + messageChain(failure));
    }

    @Test
    public void createSupportedAttributesBindsFromYaml() {
        var connector = YamlOperationsConnector.fromStrings(CREATE_YAML);
        connector.init(new SimpleConfig(wireMockServer.port()));

        stubCreateAccount();
        createWithConnIdAttribute(connector);
    }

    @Test
    public void createSupportedAttributesBindsFromGroovy() {
        var connector = YamlOperationsConnector.fromStrings()
                .withGroovyOperations(CREATE_GROOVY);
        connector.init(new SimpleConfig(wireMockServer.port()));

        stubCreateAccount();
        createWithConnIdAttribute(connector);
    }

    /**
     * Create request carrying the NAME built-in under its ConnId name — the attribute
     * restriction of the create endpoint is keyed by ConnId attribute names.
     */
    private void createWithConnIdAttribute(ClassHandlerConnectorBase connector) {
        connector.create(new ObjectClass("Account"),
                Set.of(AttributeBuilder.build(Name.NAME, "test")),
                new OperationOptionsBuilder().build());
    }

    private void stubEmbeddedAccounts() {
        wireMockServer.stubFor(get(urlEqualTo(ACCOUNTS_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(EMBEDDED_BODY)));
    }

    private ClassHandlerConnectorBase initConnectorWithYamlSearch(String yamlDocument) {
        var connector = YamlOperationsConnector.fromStrings(yamlDocument);
        connector.init(new SimpleConfig(wireMockServer.port()));
        return connector;
    }

    private List<ConnectorObject> searchAll(ClassHandlerConnectorBase connector) {
        return search(connector, null);
    }

    private static String messageChain(Throwable failure) {
        var chain = new StringBuilder();
        for (var cause = failure; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null) {
                chain.append(cause.getMessage()).append(' ');
            }
        }
        return chain.toString();
    }
}
