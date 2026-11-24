/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.crud;

import com.evolveum.polygon.scimrest.config.ScimClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.connector.AbstractGroovyRestConnector;
import com.evolveum.polygon.conndev.groovy.BaseGroovyConnectorConfiguration;
import com.evolveum.polygon.scimrest.groovy.handler.GroovyRestHandlerBuilder;
import com.evolveum.polygon.conndev.groovy.GroovyScriptLoader;
import com.evolveum.polygon.conndev.groovy.GroovySchemaLoader;
import com.evolveum.polygon.scimrest.support.AbstractScimTest;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.assertEquals;

/**
 * A full SCIM search walks the result set with {@code startIndex} (1-based) advancing by the page size.
 * The server here only answers the exact {@code startIndex} windows a correct client asks for, so a
 * client that sends page numbers instead of record offsets never gets a match and the test fails.
 */
public class ScimSearchPagingTest extends AbstractScimTest {

    private static final int TOTAL_USERS = 60;
    private static final int PAGE_SIZE = 25;

    private static final String SCHEMAS_RESPONSE = """
            {
              "schemas": ["urn:ietf:params:scim:api:messages:2.0:ListResponse"],
              "totalResults": 1,
              "Resources": [
                {
                  "schemas": ["urn:ietf:params:scim:schemas:core:2.0:Schema"],
                  "id": "urn:ietf:params:scim:schemas:core:2.0:User",
                  "name": "User",
                  "attributes": [
                    {
                      "name": "userName",
                      "type": "string",
                      "mutability": "readWrite",
                      "returned": "default",
                      "uniqueness": "server",
                      "required": true,
                      "multiValued": false,
                      "caseExact": false
                    }
                  ]
                }
              ]
            }
            """;

    private static final String OPERATION_SCRIPT = """
            objectClass("User") {
                search {
                    scim {
                        emptyFilterSupported true
                    }
                }
            }
            """;

    private static class TestConfiguration extends BaseGroovyConnectorConfiguration
            implements ScimClientConfiguration.BearerTokenAuthorization {
        private final int port;

        TestConfiguration(int port) {
            this.port = port;
        }

        @Override
        public String getScimBaseUrl() {
            return "http://localhost:" + port + SCIM_BASE_PATH;
        }

        @Override
        public GuardedString getScimTokenValue() {
            return new GuardedString("test-token".toCharArray());
        }
    }

    private static class ScriptConnector extends AbstractGroovyRestConnector {
        private final String operationScript;

        ScriptConnector(String operationScript) {
            this.operationScript = operationScript;
        }

        @Override
        protected void initializeSchema(GroovySchemaLoader loader) {
        }

        @Override
        protected void initializeAuthorizationHandler(GroovyRestHandlerBuilder builder) {
        }

        @Override
        protected void initializeObjectClassHandler(GroovyScriptLoader builder) {
            builder.loadFromString(operationScript);
        }
    }

    @BeforeMethod
    public void setUp() {
        setUpWireMock();
    }

    @AfterMethod
    public void tearDown() {
        tearDownWireMock();
    }

    @Test
    public void fullSearchReturnsEveryUserExactlyOnce() {
        stubUserDiscovery(SCHEMAS_RESPONSE);
        for (int startIndex = 1; startIndex <= TOTAL_USERS; startIndex += PAGE_SIZE) {
            wireMockServer.stubFor(get(urlPathEqualTo(USERS_ENDPOINT))
                    .withQueryParam("startIndex", equalTo(String.valueOf(startIndex)))
                    .withQueryParam("count", equalTo(String.valueOf(PAGE_SIZE)))
                    .willReturn(aResponse().withStatus(200)
                            .withHeader("Content-Type", "application/scim+json")
                            .withBody(listResponse(startIndex, Math.min(PAGE_SIZE, TOTAL_USERS - startIndex + 1)))));
        }

        var connector = new ScriptConnector(OPERATION_SCRIPT);
        connector.init(new TestConfiguration(wireMockServer.port()));

        var names = new ArrayList<String>();
        connector.executeQuery(new ObjectClass("User"), null,
                o -> { names.add(o.getName().getNameValue()); return true; },
                new OperationOptionsBuilder().build());

        List<String> expected = IntStream.rangeClosed(1, TOTAL_USERS)
                .mapToObj(i -> "user" + i)
                .collect(Collectors.toList());
        assertEquals(names, expected);
    }

    /** A ListResponse holding {@code count} users starting at {@code startIndex}, with the full total. */
    private static String listResponse(int startIndex, int count) {
        var resources = IntStream.range(startIndex, startIndex + count)
                .mapToObj(i -> """
                        {
                          "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
                          "id": "user%1$d",
                          "userName": "user%1$d"
                        }""".formatted(i))
                .collect(Collectors.joining(","));
        return """
                {
                  "schemas": ["urn:ietf:params:scim:api:messages:2.0:ListResponse"],
                  "totalResults": %d,
                  "startIndex": %d,
                  "itemsPerPage": %d,
                  "Resources": [%s]
                }
                """.formatted(TOTAL_USERS, startIndex, count, resources);
    }
}
