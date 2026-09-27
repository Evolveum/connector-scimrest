/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 */
package com.evolveum.polygon.scimrest.crud;

import com.evolveum.polygon.conndev.groovy.BaseGroovyConnectorConfiguration;
import com.evolveum.polygon.scimrest.config.ScimClientConfiguration;
import com.evolveum.polygon.scimrest.support.WireMockTestSupport;
import org.identityconnectors.common.security.GuardedString;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * End-to-end test of flattening complex attributes of a mapped SCIM extension
 * ({@code scim { extension(...) { flatten ... } }}) against a WireMock SCIM server: the
 * extension's attributes are declared in the enterprise extension schema, merged into the
 * resource's top level on the wire (RFC 7643), and flattened into plain
 * {@code enterprise_*} attributes of the object class — read back as plain values and deflattened
 * back into the extension's structures on write.
 */
public class ScimFlattenExtensionCrudTest extends WireMockTestSupport {

    private static final String SCIM_BASE_PATH = "/scim";
    private static final String SCHEMAS_ENDPOINT = SCIM_BASE_PATH + "/Schemas";
    private static final String RESOURCE_TYPES_ENDPOINT = SCIM_BASE_PATH + "/ResourceTypes";
    private static final String USERS_ENDPOINT = SCIM_BASE_PATH + "/Users";
    private static final String ENTERPRISE_URI = "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User";

    private static final String SCHEMAS_RESPONSE = """
            {
              "schemas": ["urn:ietf:params:scim:api:messages:2.0:ListResponse"],
              "totalResults": 2,
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
                      "required": false,
                      "multiValued": false
                    }
                  ]
                },
                {
                  "schemas": ["urn:ietf:params:scim:schemas:core:2.0:Schema"],
                  "id": "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User",
                  "name": "EnterpriseUser",
                  "attributes": [
                    {
                      "name": "department",
                      "type": "complex",
                      "mutability": "readWrite",
                      "returned": "default",
                      "required": false,
                      "multiValued": false,
                      "subAttributes": [
                        {
                          "name": "code",
                          "type": "string",
                          "mutability": "readWrite",
                          "returned": "default",
                          "required": false,
                          "multiValued": false
                        },
                        {
                          "name": "name",
                          "type": "string",
                          "mutability": "readWrite",
                          "returned": "default",
                          "required": false,
                          "multiValued": false
                        }
                      ]
                    },
                    {
                      "name": "photos",
                      "type": "complex",
                      "mutability": "readWrite",
                      "returned": "default",
                      "required": false,
                      "multiValued": true,
                      "subAttributes": [
                        {
                          "name": "value",
                          "type": "string",
                          "mutability": "readWrite",
                          "returned": "default",
                          "required": false,
                          "multiValued": false
                        },
                        {
                          "name": "uri",
                          "type": "string",
                          "mutability": "readWrite",
                          "returned": "default",
                          "required": false,
                          "multiValued": false
                        },
                        {
                          "name": "type",
                          "type": "string",
                          "mutability": "readWrite",
                          "returned": "default",
                          "required": false,
                          "multiValued": false
                        }
                      ]
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
                  "schema": "urn:ietf:params:scim:schemas:core:2.0:User",
                  "schemaExtensions": [
                    {
                      "schema": "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User",
                      "required": true
                    }
                  ]
                }
              ]
            }
            """;

    /** The extension's attributes are merged into the resource's top level (RFC 7643). */
    private static final String USER_RESOURCE = """
            {
              "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User",
                          "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User"],
              "id": "1",
              "userName": "jdoe",
              "department": {
                "code": "ENG",
                "name": "Engineering"
              },
              "photos": [
                { "value": "photo://work", "uri": "http://photos/work", "type": "work" },
                { "value": "photo://other", "uri": "http://photos/other", "type": "other" }
              ],
              "active": true
            }
            """;

    private static final String SEARCH_RESPONSE = """
            {
              "schemas": ["urn:ietf:params:scim:api:messages:2.0:ListResponse"],
              "totalResults": 1,
              "startIndex": 1,
              "itemsPerPage": 25,
              "Resources": [ %s ]
            }
            """.formatted(USER_RESOURCE);

    /** All connector-level flatten properties stay off; the extension block does the flattening. */
    private static final String SCHEMA_SCRIPT = """
            objectClass("User") {
                scim {
                    extension("enterprise", "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User") {
                        flatten "photos"
                        flatten "department"
                    }
                }
            }
            """;

    private static final JsonMapper MAPPER = new JsonMapper();

    /** The connector-level flatten properties are left at their (off) defaults on purpose. */
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

    @BeforeMethod
    public void setUp() {
        setUpWireMock();
    }

    @AfterMethod
    public void tearDown() {
        tearDownWireMock();
    }

    private void stubDiscovery() {
        wireMockServer.stubFor(get(urlEqualTo(SCHEMAS_ENDPOINT))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/scim+json")
                        .withBody(SCHEMAS_RESPONSE)));
        wireMockServer.stubFor(get(urlEqualTo(RESOURCE_TYPES_ENDPOINT))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/scim+json")
                        .withBody(RESOURCE_TYPES_RESPONSE)));
    }

    private ScriptConnector initConnector() {
        var connector = new ScriptConnector(null, SCHEMA_SCRIPT, null);
        connector.init(new TestConfiguration(wireMockServer.port()));
        return connector;
    }

    /*----------------------------------------------------------------------*/
    /* Read: extension flattening from the resource's top-level JSON
    /*----------------------------------------------------------------------*/

    @Test
    public void searchFlattensExtensionAttributesListedInTheExtensionBlock() {
        stubDiscovery();
        wireMockServer.stubFor(get(urlPathEqualTo(USERS_ENDPOINT))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/scim+json")
                        .withBody(SEARCH_RESPONSE)));

        var connector = initConnector();
        var results = new ArrayList<ConnectorObject>();
        connector.executeQuery(new ObjectClass("User"), null,
                o -> { results.add(o); return true; },
                new OperationOptionsBuilder().build());

        assertEquals(results.size(), 1);
        var user = results.getFirst();

        // the extension's complex attributes are flattened, alias-prefixed
        assertEquals(AttributeUtil.getSingleValue(user.getAttributeByName("enterprise_department_code")), "ENG");
        assertEquals(AttributeUtil.getSingleValue(user.getAttributeByName("enterprise_department_name")), "Engineering");
        assertEquals(AttributeUtil.getSingleValue(user.getAttributeByName("enterprise_work_photo")), "photo://work");
        assertEquals(AttributeUtil.getSingleValue(user.getAttributeByName("enterprise_other_photo_uri")), "http://photos/other");
        assertNull(user.getAttributeByName("department"), "the complex attribute itself must not be present");
        assertNull(user.getAttributeByName("photos"), "the photos complex attribute must be flattened away");

        // plain primary-schema attributes are untouched
        assertEquals(AttributeUtil.getSingleValue(user.getAttributeByName(Name.NAME)), "jdoe");
        assertEquals(AttributeUtil.getSingleValue(user.getAttributeByName("active")), Boolean.TRUE);

        // the flattened attributes are part of the schema (no embedded classes for them)
        var schema = connector.schema();
        var userAttrs = schema.findObjectClassInfo("User").getAttributeInfo().stream()
                .map(info -> info.getName()).toList();
        assertTrue(userAttrs.contains("enterprise_work_photo"));
        assertTrue(userAttrs.contains("enterprise_department_code"));
        assertNull(schema.findObjectClassInfo("User__photos"), "flattened photos has no embedded class");
        assertNull(schema.findObjectClassInfo("User__department"), "flattened department has no embedded class");
    }

    /*----------------------------------------------------------------------*/
    /* Write: deflattening into the extension's structures at the top level
    /*----------------------------------------------------------------------*/

    @Test
    public void createDeflattensExtensionAttributesListedInTheExtensionBlock() {
        stubDiscovery();
        wireMockServer.stubFor(post(urlEqualTo(USERS_ENDPOINT))
                .willReturn(aResponse().withStatus(201)
                        .withHeader("Content-Type", "application/scim+json")
                        .withBody(USER_RESOURCE)));

        var connector = initConnector();
        var uid = connector.create(new ObjectClass("User"),
                Set.of(
                        AttributeBuilder.build(Name.NAME, "jdoe"),
                        AttributeBuilder.build("enterprise_department_code", "ENG"),
                        AttributeBuilder.build("enterprise_department_name", "Engineering"),
                        AttributeBuilder.build("enterprise_work_photo", "photo://work")
                ),
                new OperationOptionsBuilder().build());

        assertEquals(uid.getUidValue(), "1");

        var requests = wireMockServer.findAll(postRequestedFor(urlEqualTo(USERS_ENDPOINT)));
        assertEquals(requests.size(), 1, "exactly one create request expected");
        var body = parse(requests.getFirst());

        // the flat values are written into the extension's structures at the resource's top level
        assertEquals(body.at("/department/code").asText(), "ENG");
        assertEquals(body.at("/department/name").asText(), "Engineering");
        assertEquals(body.at("/photos/0/type").asText(), "work");
        assertEquals(body.at("/photos/0/value").asText(), "photo://work");
        assertEquals(body.get("userName").asText(), "jdoe");

        assertFalse(body.has("department_code"), "no flat attribute may leak into the SCIM payload");
        assertFalse(body.has("enterprise_work_photo"), "no flat attribute may leak into the SCIM payload");
        assertFalse(body.has(ENTERPRISE_URI), "the extension URI must not become a JSON key");
    }

    private static ObjectNode parse(LoggedRequest request) {
        return (ObjectNode) MAPPER.readTree(request.getBody());
    }
}
