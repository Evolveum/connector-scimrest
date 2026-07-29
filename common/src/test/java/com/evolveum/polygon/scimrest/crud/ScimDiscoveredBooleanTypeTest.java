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
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

/**
 * Reproduces a real deployment: a hand-written native schema maps {@code active} via
 * {@code scim { path attribute("active") } } with no explicit type, and a separate connId schema
 * only declares UID/NAME. Live SCIM discovery ({@code /Schemas}) reports {@code active} as
 * {@code "type": "boolean"} - {@code ScimSchemaTranslator.populateAttribute()} feeds that into
 * {@code scim().type("boolean")} on the very same attribute builder. Before the {@code scim()}
 * builder was registered into {@code protocolMappings}, that information never reached the ConnId
 * schema, so midPoint was told {@code active} is a {@code String} while the connector actually
 * returned a {@code Boolean} value - exactly the "does not conform to definition... expected
 * String, actual Boolean" failure reported against a real target.
 */
public class ScimDiscoveredBooleanTypeTest extends WireMockTestSupport {

    private static final String SCIM_BASE_PATH = "/scim";
    private static final String SCHEMAS_ENDPOINT = SCIM_BASE_PATH + "/Schemas";
    private static final String RESOURCE_TYPES_ENDPOINT = SCIM_BASE_PATH + "/ResourceTypes";

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
                    },
                    {
                      "name": "active",
                      "type": "boolean",
                      "mutability": "readWrite",
                      "returned": "default",
                      "uniqueness": "server",
                      "required": false,
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

    private static final String NATIVE_SCHEMA_SCRIPT = """
            objectClass("User") {
                attribute("userName") {
                    scim { path attribute("userName") }
                }
                attribute("active") {
                    scim { path attribute("active") }
                }
            }
            """;

    private static final String CONNID_SCHEMA_SCRIPT = """
            objectClass("User") {
                connIdAttribute("UID", "id");
                connIdAttribute("NAME", "userName");
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
        @Override
        protected void initializeSchema(GroovySchemaLoader loader) {
            loader.load(NATIVE_SCHEMA_SCRIPT);
            loader.load(CONNID_SCHEMA_SCRIPT);
        }

        @Override
        protected void initializeAuthorizationHandler(GroovyRestHandlerBuilder builder) {
        }

        @Override
        protected void initializeObjectClassHandler(GroovyRestHandlerBuilder builder) {
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
    public void discoveredBooleanAttributeIsExposedAsBooleanNotString() {
        wireMockServer.stubFor(get(urlEqualTo(SCHEMAS_ENDPOINT))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/scim+json")
                        .withBody(SCHEMAS_RESPONSE)));
        wireMockServer.stubFor(get(urlEqualTo(RESOURCE_TYPES_ENDPOINT))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/scim+json")
                        .withBody(RESOURCE_TYPES_RESPONSE)));

        var connector = new ScriptConnector();
        connector.init(new TestConfiguration(wireMockServer.port()));

        var schema = connector.schema();
        var userObjectClass = schema.findObjectClassInfo("User");
        assertNotNull(userObjectClass, "User object class must be discovered");

        var activeAttribute = userObjectClass.getAttributeInfo().stream()
                .filter(a -> a.getName().equals("active"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("active attribute not found in discovered schema"));

        assertEquals(activeAttribute.getType(), Boolean.class);
    }
}
