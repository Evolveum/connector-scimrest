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
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.PermissionDeniedException;
import org.identityconnectors.framework.common.objects.*;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.*;

/** Exercises response extraction through the Groovy/YAML DSL and actual REST operations. */
public class WrappedWriteResponseTest extends AbstractCrudConnectorTest {

    private static final String SEARCH = """
            objectClass("Account") {
                search {
                    endpoint("accounts/{id}") {
                        singleResult()
                        supportedFilter(attribute("id").eq().anySingleValue()) {
                            request.pathParameter("id", value)
                        }
                    }
                }
            }
            """;
    private static final String RESOURCE = "{\"id\":\"123\",\"name\":\"FromServer\"}";
    private static final String WRAPPED = "{\"id\":\"wrong-root-id\",\"name\":\"wrong-root-name\",\"result\":" + RESOURCE + "}";
    private static final Set<AttributeDelta> SENT = Set.of(AttributeDeltaBuilder.build(Name.NAME, List.of("requested")));

    @DataProvider
    public Object[][] operations() {
        return new Object[][]{{"create"}, {"update"}};
    }

    private ClassHandlerConnectorBase connector(String operation, boolean yaml, String response) {
        var method = operation.equals("create") ? "POST" : "PATCH";
        var path = operation.equals("create") ? "accounts" : "accounts/{id}";
        if (yaml) {
            var connector = YamlOperationsConnector.fromStrings("""
                    objectClasses:
                      Account:
                        %s:
                          endpoints:
                            - method: %s
                              path: %s
                              request:
                                contentType: APPLICATION_JSON
                    %s
                    """.formatted(operation, method, path, response.indent(10)))
                    .withGroovyOperations(SEARCH);
            connector.init(new SimpleConfig(wireMockServer.port()));
            return connector;
        }
        return initConnector(SEARCH + """
                objectClass("Account") {
                    %s {
                        endpoint(%s, "%s") {
                            request { contentType APPLICATION_JSON }
                            %s
                        }
                    }
                }
                """.formatted(operation, method, path, response));
    }

    private void stubResponse(String operation, int status, String body) {
        wireMockServer.stubFor(get(urlEqualTo(ACCOUNT_BY_ID_PATH)).willReturn(okJson(RESOURCE)));
        var request = operation.equals("create") ? post(urlEqualTo(ACCOUNTS_PATH)) : patch(urlEqualTo(ACCOUNT_BY_ID_PATH));
        wireMockServer.stubFor(request.willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", "application/json")
                .withHeader("Location", "/accounts/456")
                .withBody(body)));
    }

    private Object invoke(String operation, ClassHandlerConnectorBase connector) {
        var options = new OperationOptionsBuilder().build();
        return operation.equals("create")
                ? connector.create(new ObjectClass("Account"), Set.of(new Name("requested")), options)
                : connector.updateDelta(new ObjectClass("Account"), new Uid("123"), SENT, options);
    }

    private void assertMapped(String operation, ClassHandlerConnectorBase connector) {
        var expected = operation.equals("create") ? new Uid("123")
                : Set.of(AttributeDeltaBuilder.build(Name.NAME, List.of("FromServer")));
        assertEquals(invoke(operation, connector), expected);
        var request = operation.equals("create") ? postRequestedFor(urlEqualTo(ACCOUNTS_PATH))
                : patchRequestedFor(urlEqualTo(ACCOUNT_BY_ID_PATH));
        wireMockServer.verify(1, request.withRequestBody(equalToJson("{\"name\":\"requested\"}")));
    }

    @Test(dataProvider = "operations")
    public void wrappedResponseFromGroovy(String operation) {
        stubResponse(operation, 200, WRAPPED);
        assertMapped(operation, connector(operation, false, "response { objectPath '$.result' }"));
    }

    @Test(dataProvider = "operations")
    public void wrappedResponseFromYamlPointer(String operation) {
        stubResponse(operation, 200, WRAPPED);
        assertMapped(operation, connector(operation, true, """
                response:
                  objectPath:
                    type: JSON_POINTER
                    value: /result
                """));
    }

    @Test(dataProvider = "operations")
    public void nestedResponseFromYamlJsonPath(String operation) {
        stubResponse(operation, 200, "{\"payload\":{\"result\":" + RESOURCE + "}}");
        assertMapped(operation, connector(operation, true, "response:\n  objectPath: $.payload.result"));
    }

    @Test(dataProvider = "operations")
    public void unwrappedResponseRemainsDefault(String operation) {
        stubResponse(operation, 200, RESOURCE);
        assertMapped(operation, connector(operation, false, ""));
    }

    @DataProvider
    public Object[][] invalidBodies() {
        return new Object[][]{
                {"create", "{}"}, {"update", "{}"},
                {"create", "{\"result\":null}"}, {"update", "{\"result\":null}"},
                {"create", "{\"result\":[]}"}, {"update", "{\"result\":[]}"},
                {"create", "{\"result\":\"text\"}"}, {"update", "{\"result\":\"text\"}"}
        };
    }

    @Test(dataProvider = "invalidBodies")
    public void selectedResourceMustBeAnObject(String operation, String body) {
        stubResponse(operation, 200, body);
        var exception = expectThrows(ConnectorException.class,
                () -> invoke(operation, connector(operation, false, "response { objectPath '$.result' }")));
        assertTrue(exception.getMessage().contains("$.result"), exception.getMessage());
        assertTrue(exception.getMessage().contains("accounts"), exception.getMessage());
        assertTrue(exception.getMessage().contains("JSON object"), exception.getMessage());
    }

    @Test(dataProvider = "operations")
    public void invalidPointerFailsBeforeAnyHttpRequest(String operation) {
        var exception = expectThrows(ConnectorException.class, () -> invoke(operation, connector(operation, true, """
                response:
                  objectPath:
                    type: JSON_POINTER
                    value: not-a-pointer
                """)));
        assertTrue(exception.getMessage().contains("not-a-pointer"), exception.getMessage());
        assertEquals(wireMockServer.getAllServeEvents().size(), 0);
    }

    @Test(dataProvider = "operations")
    public void emptyBodyKeepsExistingFallback(String operation) {
        stubResponse(operation, operation.equals("create") ? 201 : 204, "");
        assertEquals(invoke(operation, connector(operation, false, "response { objectPath '$.result' }")),
                operation.equals("create") ? new Uid("456") : SENT);
    }

    @Test(dataProvider = "operations")
    public void errorResponseIsMappedBeforeResourceExtraction(String operation) {
        stubResponse(operation, 403, "{\"error\":\"Access denied\"}");
        expectThrows(PermissionDeniedException.class,
                () -> invoke(operation, connector(operation, false, "response { objectPath '$.result' }")));
    }
}
