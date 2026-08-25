/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.crud;

import com.evolveum.polygon.conndev.groovy.BaseGroovyConnectorConfiguration;
import com.evolveum.polygon.conndev.groovy.GroovySchemaLoader;
import com.evolveum.polygon.scimrest.config.ScimClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.connector.AbstractGroovyRestConnector;
import com.evolveum.polygon.scimrest.groovy.handler.GroovyRestHandlerBuilder;
import com.evolveum.polygon.scimrest.support.WireMockTestSupport;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeDeltaBuilder;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.EmbeddedObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.Uid;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * End-to-end test of the optional "flatten single-value complex attributes" SCIM mapping rule
 * against a WireMock SCIM server:
 * <ul>
 *   <li>read: {@code name/formatted} &amp; co. are surfaced as plain {@code name_*} attributes
 *       instead of an embedded {@code name} object; multi-valued complex {@code emails} keeps
 *       its embedded object mapping;</li>
 *   <li>write: flat {@code name_*} attributes are deflattened back into the nested
 *       {@code name} object in the SCIM request bodies of create and update.</li>
 * </ul>
 */
public class ScimFlattenSingleValueComplexCrudTest extends WireMockTestSupport {

    private static final String SCIM_BASE_PATH = "/scim";
    private static final String SCHEMAS_ENDPOINT = SCIM_BASE_PATH + "/Schemas";
    private static final String RESOURCE_TYPES_ENDPOINT = SCIM_BASE_PATH + "/ResourceTypes";
    private static final String USERS_ENDPOINT = SCIM_BASE_PATH + "/Users";
    private static final String USER_BY_ID_ENDPOINT = SCIM_BASE_PATH + "/Users/1";

    private static final String USER_URN = "urn:ietf:params:scim:schemas:core:2.0:User";

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
                          "name": "familyName",
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
                        },
                        {
                          "name": "middleName",
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
                "familyName": "Doe",
                "givenName": "John",
                "middleName": "Q"
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

    private static final JsonMapper MAPPER = new JsonMapper();

    private static class TestConfiguration extends BaseGroovyConnectorConfiguration
            implements ScimClientConfiguration {
        private final int port;

        TestConfiguration(int port) {
            this.port = port;
        }

        @Override
        public String getScimBaseUrl() {
            return "http://localhost:" + port + SCIM_BASE_PATH;
        }

        @Override
        public Boolean getScimFlattenSingleValueComplexAttributes() {
            return Boolean.TRUE;
        }
    }

    private static class TestConnector extends AbstractGroovyRestConnector<TestConfiguration> {

        @Override
        protected void initializeSchema(GroovySchemaLoader loader) {
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

    private TestConnector initConnector() {
        var connector = new TestConnector();
        connector.init(new TestConfiguration(wireMockServer.port()));
        return connector;
    }

    /*----------------------------------------------------------------------*/
    /* Read: flattening
    /*----------------------------------------------------------------------*/

    @Test
    public void searchFlattensSingleValueComplexAttributes() {
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

        // single-valued complex 'name' is flattened into plain attributes
        assertEquals(AttributeUtil.getSingleValue(user.getAttributeByName("name_formatted")), "John Doe");
        assertEquals(AttributeUtil.getSingleValue(user.getAttributeByName("name_familyName")), "Doe");
        assertEquals(AttributeUtil.getSingleValue(user.getAttributeByName("name_givenName")), "John");
        assertEquals(AttributeUtil.getSingleValue(user.getAttributeByName("name_middleName")), "Q");
        assertNull(user.getAttributeByName("name"), "the complex attribute itself must not be present");

        // plain attributes are untouched
        assertEquals(AttributeUtil.getSingleValue(user.getAttributeByName(Name.NAME)), "jdoe");
        assertEquals(AttributeUtil.getSingleValue(user.getAttributeByName("active")), Boolean.TRUE);

        // multi-valued complex 'emails' keeps its embedded object mapping
        var emailsAttr = user.getAttributeByName("emails");
        assertNotNull(emailsAttr, "emails attribute must be present");
        var emails = (EmbeddedObject) AttributeUtil.getSingleValue(emailsAttr);
        assertEquals(emails.getObjectClass().getObjectClassValue(), "User__emails");
        assertEquals(AttributeUtil.getStringValue(emails.getAttributeByName("value")), "john.doe@example.com");

        // the flattened attribute replaced the embedded class in the schema
        var schema = connector.schema();
        var userAttrs = schema.findObjectClassInfo("User").getAttributeInfo().stream()
                .map(info -> info.getName()).toList();
        assertTrue(userAttrs.contains("name_formatted"));
        assertFalse(userAttrs.contains("name"));
        assertNull(schema.findObjectClassInfo("User__name"), "no embedded class for flattened attribute");
        assertNotNull(schema.findObjectClassInfo("User__emails"), "multi-valued complex still embedded");
    }

    /*----------------------------------------------------------------------*/
    /* Write: deflattening
    /*----------------------------------------------------------------------*/

    @Test
    public void createDeflattensAttributesIntoNestedJson() {
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
                        AttributeBuilder.build("name_familyName", "Doe")
                ),
                new OperationOptionsBuilder().build());

        assertEquals(uid.getUidValue(), "1");

        var requests = wireMockServer.findAll(postRequestedFor(urlEqualTo(USERS_ENDPOINT)));
        assertEquals(requests.size(), 1, "exactly one create request expected");
        var body = parse(requests.get(0));

        // flat attributes are deflattened into the nested 'name' object
        assertEquals(body.at("/name/formatted").asText(), "John Doe");
        assertEquals(body.at("/name/familyName").asText(), "Doe");
        assertEquals(body.get("userName").asText(), "jdoe");
        assertFalse(body.has("name_formatted"), "no flat attribute may leak into the SCIM payload");
        assertFalse(body.has("name_familyName"), "no flat attribute may leak into the SCIM payload");
        assertFalse(body.path("name").has("givenName"), "attributes that were not provided must not be sent");
    }

    @Test
    public void updateDeflattensAttributesIntoNestedJson() {
        stubDiscovery();
        wireMockServer.stubFor(get(urlEqualTo(USER_BY_ID_ENDPOINT))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/scim+json")
                        .withBody(USER_RESOURCE)));
        wireMockServer.stubFor(put(urlEqualTo(USER_BY_ID_ENDPOINT))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/scim+json")
                        .withBody(USER_RESOURCE)));

        var connector = initConnector();
        connector.updateDelta(new ObjectClass("User"),
                new Uid("1"),
                Set.of(AttributeDeltaBuilder.build("name_formatted", List.of("Jane Doe"))),
                new OperationOptionsBuilder().build());

        // the original state is read via the retrieve endpoint (flattened on the way in)
        assertEquals(wireMockServer.findAll(getRequestedFor(urlEqualTo(USER_BY_ID_ENDPOINT))).size(), 1);

        var requests = wireMockServer.findAll(putRequestedFor(urlEqualTo(USER_BY_ID_ENDPOINT)));
        assertEquals(requests.size(), 1, "exactly one update request expected");
        var body = parse(requests.get(0));

        assertEquals(body.at("/name/formatted").asText(), "Jane Doe");
        assertEquals(body.get("id").asText(), "1");
        assertFalse(body.has("name_formatted"), "no flat attribute may leak into the SCIM payload");
    }

    private static ObjectNode parse(LoggedRequest request) {
        return (ObjectNode) MAPPER.readTree(request.getBody());
    }
}
