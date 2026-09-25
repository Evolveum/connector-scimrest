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

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.testng.Assert.assertEquals;

/**
 * A bare {@code search { scim { } } } block - no {@code emptyFilterSupported}, no
 * {@code limitations} - still has to serve a filterless ("search all") {@code executeQuery}: its
 * {@code ScimSearchHandler} never registers as the dispatcher's dedicated empty-filter slot
 * ({@code emptyFilterSupported()} defaults to {@code false}), so a {@code null} filter only
 * reaches it via {@link com.evolveum.polygon.conndev.spi.FilterBasedSearchDispatcher}'s
 * {@code anyFilterHandler} fallback ({@code anyFilterSupported()} defaults to {@code true}).
 * This pins down the exact request that fallback path produces, since nothing else did.
 */
public class ScimSearchAllDefaultScimBlockTest extends AbstractScimTest {

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

    private static final String SEARCH_RESPONSE = """
            {
              "schemas": ["urn:ietf:params:scim:api:messages:2.0:ListResponse"],
              "totalResults": 1,
              "startIndex": 1,
              "itemsPerPage": 1,
              "Resources": [
                {
                  "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
                  "id": "jdoe",
                  "userName": "jdoe"
                }
              ]
            }
            """;

    // No emptyFilterSupported, no limitations - the bare block an LLM-generated "empty" script
    // reduces to.
    private static final String OPERATION_SCRIPT = """
            objectClass("User") {
                search {
                    scim {
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
        stubUserDiscovery(SCHEMAS_RESPONSE);
        wireMockServer.stubFor(get(urlPathEqualTo(USERS_ENDPOINT))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/scim+json")
                        .withBody(SEARCH_RESPONSE)));
    }

    @AfterMethod
    public void tearDown() {
        tearDownWireMock();
    }

    @Test
    public void nullFilterFallsThroughToAnyFilterHandlerAndSendsPlainPagedListRequest() {
        var connector = new ScriptConnector(OPERATION_SCRIPT);
        connector.init(new TestConfiguration(wireMockServer.port()));

        var results = new ArrayList<ConnectorObject>();
        connector.executeQuery(new ObjectClass("User"), null,
                o -> { results.add(o); return true; },
                new OperationOptionsBuilder().build());

        assertEquals(results.size(), 1);

        // Exactly one page fetched (totalResults == itemsPerPage == 1), with ConnId's default
        // page size (25) and no "filter" query param at all - not even filter=null/empty.
        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(USERS_ENDPOINT))
                .withQueryParam("startIndex", equalTo("1"))
                .withQueryParam("count", equalTo("25"))
                .withQueryParam("filter", absent())).size(), 1);
    }
}
