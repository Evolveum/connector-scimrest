/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.crud;

import com.evolveum.polygon.scimrest.config.ScimClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.AbstractGroovyRestConnector;
import com.evolveum.polygon.scimrest.groovy.BaseGroovyConnectorConfiguration;
import com.evolveum.polygon.scimrest.groovy.GroovyRestHandlerBuilder;
import com.evolveum.polygon.scimrest.groovy.GroovySchemaLoader;
import com.evolveum.polygon.scimrest.support.WireMockTestSupport;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.assertEquals;

/**
 * {@code GenericScimObjectDeserializer} (scim2-sdk-common) reads every {@code ListResponse.Resources}
 * element via {@code ObjectReader.readValue(JsonParser)} on a shared, mid-stream parser. Jackson 3's
 * default {@code DeserializationFeature.FAIL_ON_TRAILING_TOKENS} then misfires as soon as more content
 * follows that element in the stream - which is exactly what happens whenever a search response carries
 * more than one resource. This test drives a real two-resource SCIM search response through WireMock and
 * verifies it deserializes cleanly instead of throwing {@code ScimDeserializeException}.
 */
public class ScimMultipleSearchResultsTest extends WireMockTestSupport {

    private static final String SCIM_BASE_PATH = "/scim";
    private static final String SCHEMAS_ENDPOINT = SCIM_BASE_PATH + "/Schemas";
    private static final String RESOURCE_TYPES_ENDPOINT = SCIM_BASE_PATH + "/ResourceTypes";
    private static final String USERS_ENDPOINT = SCIM_BASE_PATH + "/Users";

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

    private static final String RESOURCE_TYPES_RESPONSE = """
            {
              "schemas": ["urn:ietf:params:scim:api:messages:2.0:ListResponse"],
              "totalResults": 1,
              "Resources": [
                {
                  "schemas": ["urn:ietf:params:scim:schemas:core:2.0:ResourceType"],
                  "id": "User",
                  "name": "User",
                  "endpoint": "/Users",
                  "schema": "urn:ietf:params:scim:schemas:core:2.0:User"
                }
              ]
            }
            """;

    // Mirrors the report: two resources in one page - the first resource's closing '}' is
    // immediately followed by a trailing token (the second resource's '{') in the shared parser
    // stream, which is exactly what trips FAIL_ON_TRAILING_TOKENS.
    private static final String MULTI_RESULT_SEARCH_RESPONSE = """
            {
              "schemas": ["urn:ietf:params:scim:api:messages:2.0:ListResponse"],
              "totalResults": 2,
              "startIndex": 1,
              "itemsPerPage": 2,
              "Resources": [
                {
                  "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
                  "id": "jdoe",
                  "userName": "jdoe"
                },
                {
                  "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
                  "id": "asmith",
                  "userName": "asmith"
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

    private static class TestConfiguration extends BaseGroovyConnectorConfiguration implements ScimClientConfiguration {
        private final int port;

        TestConfiguration(int port) {
            this.port = port;
        }

        @Override
        public String getScimBaseUrl() {
            return "http://localhost:" + port + SCIM_BASE_PATH;
        }
    }

    private static class ScriptConnector extends AbstractGroovyRestConnector<TestConfiguration> {
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
        protected void initializeObjectClassHandler(GroovyRestHandlerBuilder builder) {
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
    public void searchResponseWithMultipleResourcesDeserializesWithoutTrailingTokenError() {
        wireMockServer.stubFor(get(urlEqualTo(SCHEMAS_ENDPOINT))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/scim+json")
                        .withBody(SCHEMAS_RESPONSE)));
        wireMockServer.stubFor(get(urlEqualTo(RESOURCE_TYPES_ENDPOINT))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/scim+json")
                        .withBody(RESOURCE_TYPES_RESPONSE)));
        wireMockServer.stubFor(get(urlPathEqualTo(USERS_ENDPOINT))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/scim+json")
                        .withBody(MULTI_RESULT_SEARCH_RESPONSE)));

        var connector = new ScriptConnector(OPERATION_SCRIPT);
        connector.init(new TestConfiguration(wireMockServer.port()));

        var results = new ArrayList<ConnectorObject>();
        connector.executeQuery(new ObjectClass("User"), null,
                o -> { results.add(o); return true; },
                new OperationOptionsBuilder().build());

        assertEquals(results.size(), 2);
    }
}
