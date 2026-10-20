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
import org.identityconnectors.framework.common.objects.AttributeDeltaBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.Uid;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.expectThrows;
import static org.testng.Assert.assertTrue;

/**
 * Checks the {@code pathParameters:} binding of single-object endpoints
 * ({@link com.evolveum.polygon.scimrest.yaml.binding.PathParametersHandler}): the YAML mapping of
 * a path placeholder to an attribute name must reach {@code pathParameter(name, DefinitionValue)},
 * so the declared attribute name keeps the YAML location the user wrote it at.
 */
public class YamlPathParametersBindingTest extends AbstractCrudConnectorTest {

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

    private static final String UPDATE_YAML_PATH_PARAM_ATTR = """
            objectClasses:
              Account:
                update:
                  endpoints:
                    - method: PATCH
                      path: accounts/{attribute}
                      pathParameters:
                        attribute: __NAME__
                      request:
                        contentType: application/json
            """;

    private static final String UPDATE_YAML_PATH_PARAM_BAD_ATTR = """
            objectClasses:
              Account:
                update:
                  endpoints:
                    - method: PATCH
                      path: accounts/{attribute}
                      pathParameters:
                        attribute: naame
                      request:
                        contentType: application/json
            """;

    @Test
    public void yamlPathParameterBindsTheAttributeExtractor() {
        stubOldObject();

        var connector = initWithYaml(SEARCH_BY_ID_YAML, UPDATE_YAML_PATH_PARAM_ATTR);
        updateName(connector);

        assertEquals(wireMockServer.findAll(patchRequestedFor(urlEqualTo(ACCOUNT_BY_OLD_NAME_PATH))
                        .withHeader("Content-Type", equalTo("application/json"))
                        .withRequestBody(matchingJsonPath("$.name", equalTo("new-name")))).size(), 1,
                "the PATCH request must target the attribute value resolved from the old object");
    }

    @Test
    public void yamlPathParameterFailureCarriesTheSourceLocation() {
        stubOldObject();

        var connector = initWithYaml(SEARCH_BY_ID_YAML, UPDATE_YAML_PATH_PARAM_BAD_ATTR);

        var failure = expectThrows(Exception.class, () -> updateName(connector));

        var message = messageChain(failure);
        assertTrue(message.contains("Attribute 'naame' not found in connector object."),
                "expected the unknown-attribute failure, got: " + message);
        int badLine = yamlLineOf(UPDATE_YAML_PATH_PARAM_BAD_ATTR, "attribute: naame");
        assertTrue(message.matches(".*declared at .*" + badLine + ".*"),
                "expected the YAML line of the mapping in the failure, got: " + message);
    }

    private void stubOldObject() {
        // read of the current object (required by the update flow)
        wireMockServer.stubFor(get(urlEqualTo(ACCOUNT_BY_ID_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"123\",\"name\":\"old-name\"}")));
        // the PATCH itself, targeted via the resolved attribute value
        wireMockServer.stubFor(patch(urlPathMatching(ACCOUNTS_PATTERN))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"123\",\"name\":\"new-name\"}")));
    }

    private ClassHandlerConnectorBase initWithYaml(String... yamlDocuments) {
        var connector = YamlOperationsConnector.fromStrings(yamlDocuments);
        connector.init(new SimpleConfig(wireMockServer.port()));
        return connector;
    }

    private void updateName(ClassHandlerConnectorBase connector) {
        connector.updateDelta(new ObjectClass("Account"),
                new Uid("123"),
                Set.of(AttributeDeltaBuilder.build(Name.NAME, List.of("new-name"))),
                new OperationOptionsBuilder().build());
    }

    private static int yamlLineOf(String document, String marker) {
        var lines = document.split("\n", -1);
        for (var i = 0; i < lines.length; i++) {
            if (lines[i].contains(marker)) {
                return i + 1;
            }
        }
        throw new AssertionError("Marker not found in document: " + marker);
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
