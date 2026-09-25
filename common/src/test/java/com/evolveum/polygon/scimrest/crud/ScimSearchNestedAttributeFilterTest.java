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
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Verifies that filters on attributes mapped to nested SCIM attribute paths (via
 * {@code scim { path("...") } } using the SCIM path grammar) are translated into SCIM
 * filter expressions on the nested path, e.g. {@code name.givenName eq "Jane"}. Value
 * paths (e.g. {@code emails[type eq "work"].value}) cannot be filtered on and are
 * rejected.
 */
public class ScimSearchNestedAttributeFilterTest extends AbstractScimTest {

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
                      "name": "id",
                      "type": "string",
                      "mutability": "readOnly",
                      "returned": "always",
                      "required": true,
                      "multiValued": false
                    },
                    {
                      "name": "userName",
                      "type": "string",
                      "mutability": "readWrite",
                      "returned": "default",
                      "uniqueness": "server",
                      "required": true,
                      "multiValued": false,
                      "caseExact": false
                    },
                    {
                      "name": "name",
                      "type": "complex",
                      "mutability": "readWrite",
                      "returned": "default",
                      "multiValued": false,
                      "subAttributes": [
                        { "name": "givenName", "type": "string", "mutability": "readWrite", "returned": "default", "multiValued": false }
                      ]
                    },
                    {
                      "name": "emails",
                      "type": "complex",
                      "mutability": "readWrite",
                      "returned": "default",
                      "multiValued": true,
                      "subAttributes": [
                        { "name": "value", "type": "string", "mutability": "readWrite", "returned": "default", "multiValued": false },
                        { "name": "type", "type": "string", "mutability": "readWrite", "returned": "default", "multiValued": false }
                      ]
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
              "Resources": [
                {
                  "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
                  "id": "1",
                  "userName": "jdoe",
                  "name": { "givenName": "Jane" }
                }
              ]
            }
            """;

    private static final String SCHEMA_SCRIPT = """
            objectClass("User") {
                attribute("userName") {
                    scim { path("userName") type "string" }
                }
                attribute("givenName") {
                    scim { path("name.givenName") type "string" }
                }
                attribute("workEmail") {
                    scim { path("emails[type eq \\"work\\"].value") type "string" }
                }
            }
            """;

    private static class TestConfiguration extends BaseGroovyConnectorConfiguration implements ScimClientConfiguration.BearerTokenAuthorization {
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

        @Override
        protected void initializeSchema(GroovySchemaLoader loader) {
            loader.load(SCHEMA_SCRIPT);
        }

        @Override
        protected void initializeAuthorizationHandler(GroovyRestHandlerBuilder builder) {
        }

        @Override
        protected void initializeObjectClassHandler(GroovyScriptLoader builder) {
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
    public void filterOnNestedPathAttributeIsTranslatedToNestedScimPath() {
        var connector = new ScriptConnector();
        connector.init(new TestConfiguration(wireMockServer.port()));

        var results = new ArrayList<ConnectorObject>();
        connector.executeQuery(new ObjectClass("User"),
                new EqualsFilter(
                        AttributeBuilder.build("givenName", "Jane")),
                o -> {
                    results.add((ConnectorObject) o);
                    return true;
                },
                new OperationOptionsBuilder().build());

        assertEquals(results.size(), 1);
        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(USERS_ENDPOINT))
                .withQueryParam("filter", equalTo("name.givenName eq \"Jane\""))).size(), 1);
    }

    @Test
    public void filterOnValuePathAttributeIsRejectedWithoutSearching() {
        var connector = new ScriptConnector();
        connector.init(new TestConfiguration(wireMockServer.port()));

        var exception = Assert.expectThrows(Exception.class, () -> connector.executeQuery(new ObjectClass("User"),
                new EqualsFilter(
                        AttributeBuilder.build("workEmail", "jdoe@example.com")),
                o -> true,
                new OperationOptionsBuilder().build()));

        assertTrue(exception.getCause() instanceof IllegalArgumentException);
        assertTrue(exception.getCause().getMessage().contains("workEmail"));
        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(USERS_ENDPOINT))).size(), 0);
    }
}
