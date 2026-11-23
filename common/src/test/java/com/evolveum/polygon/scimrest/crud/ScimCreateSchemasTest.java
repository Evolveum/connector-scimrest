/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.crud;

import com.evolveum.polygon.conndev.groovy.BaseGroovyConnectorConfiguration;
import com.evolveum.polygon.conndev.groovy.GroovyScriptLoader;
import com.evolveum.polygon.conndev.groovy.GroovySchemaLoader;
import com.evolveum.polygon.scimrest.config.ScimClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.connector.AbstractGroovyRestConnector;
import com.evolveum.polygon.scimrest.groovy.handler.GroovyRestHandlerBuilder;
import com.evolveum.polygon.scimrest.support.AbstractScimTest;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.testng.Assert.assertEquals;

/**
 * Regression test for the SCIM create operation omitting the mandatory {@code schemas} attribute
 * (bug #13504): the built-in create handler must declare the resource's primary schema (determined
 * from the endpoint) plus the URN of every schema extension referenced by an attribute that is
 * actually being set. A create that sets only core attributes declares only the primary schema;
 * one that sets an extension attribute also declares that extension's schema.
 */
public class ScimCreateSchemasTest extends AbstractScimTest {

    private static final String CORE_USER_URN = "urn:ietf:params:scim:schemas:core:2.0:User";
    private static final String ENTERPRISE_URN = "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User";

    /** Discovery: the User resource (core User schema) with the enterprise extension advertised. */
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
                    { "name": "userName", "type": "string",  "mutability": "readWrite", "required": true,  "multiValued": false },
                    { "name": "active",   "type": "boolean", "mutability": "readWrite", "required": false, "multiValued": false }
                  ]
                },
                {
                  "schemas": ["urn:ietf:params:scim:schemas:core:2.0:Schema"],
                  "id": "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User",
                  "name": "EnterpriseUser",
                  "attributes": [
                    { "name": "employeeNumber", "type": "string", "mutability": "readWrite", "required": false, "multiValued": false }
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
                    { "schema": "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User", "required": true }
                  ]
                }
              ]
            }
            """;

    private static final String CREATED_USER = """
            {
              "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
              "id": "1",
              "userName": "new.user"
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
                attribute("employeeNumber") {
                    scim { path "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:employeeNumber" }
                }
            }
            """;

    private static final String CONNID_SCHEMA_SCRIPT = """
            objectClass("User") {
                connIdAttribute("UID", "id");
                connIdAttribute("NAME", "userName");
            }
            """;

    private static final JsonMapper MAPPER = new JsonMapper();

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
        @Override
        protected void initializeSchema(GroovySchemaLoader loader) {
            loader.load(NATIVE_SCHEMA_SCRIPT);
            loader.load(CONNID_SCHEMA_SCRIPT);
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

    private void stubCreate() {
        wireMockServer.stubFor(post(urlEqualTo(USERS_ENDPOINT))
                .willReturn(aResponse().withStatus(201)
                        .withHeader("Content-Type", "application/scim+json")
                        .withHeader("Location", "http://localhost" + SCIM_BASE_PATH + "/Users/1")
                        .withBody(CREATED_USER)));
    }

    @Test
    public void createDeclaresOnlyThePrimarySchemaWhenNoExtensionAttributeIsSet() {
        stubDiscovery();
        stubCreate();

        var connector = new ScriptConnector();
        connector.init(new TestConfiguration(wireMockServer.port()));

        connector.create(new ObjectClass("User"),
                Set.of(
                        AttributeBuilder.build(Name.NAME, "new.user"),
                        AttributeBuilder.build("active", true)
                ),
                new OperationOptionsBuilder().build());

        var body = parse(wireMockServer.findAll(postRequestedFor(urlEqualTo(USERS_ENDPOINT))).getFirst());
        assertEquals(schemas(body), List.of(CORE_USER_URN),
                "a create that sets no extension attributes must declare only the primary schema");
    }

    @Test
    public void createDeclaresTheExtensionSchemaWhenAnExtensionAttributeIsSet() {
        stubDiscovery();
        stubCreate();

        var connector = new ScriptConnector();
        connector.init(new TestConfiguration(wireMockServer.port()));

        connector.create(new ObjectClass("User"),
                Set.of(
                        AttributeBuilder.build(Name.NAME, "new.user"),
                        AttributeBuilder.build("employeeNumber", "E123")
                ),
                new OperationOptionsBuilder().build());

        var body = parse(wireMockServer.findAll(postRequestedFor(urlEqualTo(USERS_ENDPOINT))).getFirst());
        assertEquals(schemas(body), List.of(CORE_USER_URN, ENTERPRISE_URN),
                "a create that sets an extension attribute must declare the primary and the extension schemas");
        assertEquals(body.get("employeeNumber").asText(), "E123",
                "the extension attribute must be written at the resource's top level");
    }

    /** Reads the {@code schemas} array of the request body, in order. */
    private static List<String> schemas(ObjectNode body) {
        var list = new ArrayList<String>();
        for (var element : body.get("schemas")) {
            list.add(element.asText());
        }
        return list;
    }

    private static ObjectNode parse(LoggedRequest request) {
        return (ObjectNode) MAPPER.readTree(request.getBody());
    }
}
