/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
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
 * End-to-end test of the per-object-class {@code scim { flatten ... }} configuration against a
 * WireMock SCIM server: with every connector-level {@code SCIM Mapping} flatten property off
 * (the defaults), the object-class schema script enables flattening of {@code name} and
 * {@code emails} for {@code User} only — the complex attributes are read as plain
 * {@code name_*}/{@code <type>_email} attributes and deflattened back into the nested SCIM
 * structures on write.
 */
public class ScimFlattenPerObjectClassCrudTest extends WireMockTestSupport {

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
                    },
                    {
                      "name": "name",
                      "type": "complex",
                      "mutability": "readWrite",
                      "returned": "default",
                      "required": false,
                      "multiValued": false,
                      "subAttributes": [
                        {
                          "name": "formatted",
                          "type": "string",
                          "mutability": "readWrite",
                          "returned": "default",
                          "required": false,
                          "multiValued": false
                        },
                        {
                          "name": "givenName",
                          "type": "string",
                          "mutability": "readWrite",
                          "returned": "default",
                          "required": false,
                          "multiValued": false
                        }
                      ]
                    },
                    {
                      "name": "emails",
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
                          "name": "type",
                          "type": "string",
                          "mutability": "readWrite",
                          "returned": "default",
                          "required": false,
                          "multiValued": false
                        }
                      ]
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

    private static final String USER_RESOURCE = """
            {
              "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
              "id": "1",
              "userName": "jdoe",
              "name": {
                "formatted": "John Doe",
                "givenName": "John"
              },
              "emails": [
                { "value": "john.doe@example.com", "type": "work" }
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

    /** All connector-level flatten properties stay off — only the object-class list enables flattening. */
    private static final String SCHEMA_SCRIPT = """
            objectClass("User") {
                scim {
                    flatten "name"
                    flatten "emails"
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
    /* Read: per-object-class flattening
    /*----------------------------------------------------------------------*/

    @Test
    public void searchFlattensComplexAttributesListedInTheObjectClass() {
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

        // the object-class-listed complex attributes are flattened despite the global properties being off
        assertEquals(AttributeUtil.getSingleValue(user.getAttributeByName("name_formatted")), "John Doe");
        assertEquals(AttributeUtil.getSingleValue(user.getAttributeByName("name_givenName")), "John");
        assertNull(user.getAttributeByName("name"), "the complex attribute itself must not be present");
        assertEquals(AttributeUtil.getSingleValue(user.getAttributeByName("work_email")), "john.doe@example.com");
        assertNull(user.getAttributeByName("emails"), "the emails complex attribute must be flattened away");

        // plain attributes are untouched
        assertEquals(AttributeUtil.getSingleValue(user.getAttributeByName(Name.NAME)), "jdoe");
        assertEquals(AttributeUtil.getSingleValue(user.getAttributeByName("active")), Boolean.TRUE);

        // the flattened attributes replaced the embedded classes in the schema
        var schema = connector.schema();
        var userAttrs = schema.findObjectClassInfo("User").getAttributeInfo().stream()
                .map(info -> info.getName()).toList();
        assertTrue(userAttrs.contains("name_formatted"));
        assertTrue(userAttrs.contains("work_email"));
        assertFalse(userAttrs.contains("name"));
        assertNull(schema.findObjectClassInfo("User__name"), "no embedded class for flattened attribute");
        assertNull(schema.findObjectClassInfo("User__emails"), "flattened emails has no embedded class");
    }

    /*----------------------------------------------------------------------*/
    /* Write: deflattening
    /*----------------------------------------------------------------------*/

    @Test
    public void createDeflattensAttributesListedInTheObjectClass() {
        stubDiscovery();
        wireMockServer.stubFor(post(urlEqualTo(USERS_ENDPOINT))
                .willReturn(aResponse().withStatus(201)
                        .withHeader("Content-Type", "application/scim+json")
                        .withBody(USER_RESOURCE)));

        var connector = initConnector();
        var uid = connector.create(new ObjectClass("User"),
                Set.of(
                        AttributeBuilder.build(Name.NAME, "jdoe"),
                        AttributeBuilder.build("name_formatted", "John Doe"),
                        AttributeBuilder.build("work_email", "john.doe@example.com")
                ),
                new OperationOptionsBuilder().build());

        assertEquals(uid.getUidValue(), "1");

        var requests = wireMockServer.findAll(postRequestedFor(urlEqualTo(USERS_ENDPOINT)));
        assertEquals(requests.size(), 1, "exactly one create request expected");
        var body = parse(requests.getFirst());

        assertEquals(body.at("/name/formatted").asText(), "John Doe");
        assertEquals(body.get("userName").asText(), "jdoe");
        assertEquals(body.at("/emails/0/type").asText(), "work");
        assertEquals(body.at("/emails/0/value").asText(), "john.doe@example.com");
        assertFalse(body.has("name_formatted"), "no flat attribute may leak into the SCIM payload");
        assertFalse(body.has("work_email"), "no flat attribute may leak into the SCIM payload");
    }

    private static ObjectNode parse(LoggedRequest request) {
        return (ObjectNode) MAPPER.readTree(request.getBody());
    }
}
