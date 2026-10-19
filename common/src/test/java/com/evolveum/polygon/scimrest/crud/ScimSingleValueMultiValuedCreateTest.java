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
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.Arrays;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Reproduces a real deployment: a {@code products} attribute is discovered as
 * {@code "multiValued": true} and the ConnId schema correctly reflects it, but creating an object
 * with only one value for it sends a plain scalar in the request body instead of a one-element
 * array. With more than one value an array is produced correctly - the single-value case is the
 * one that drops the multivalue-ness.
 */
public class ScimSingleValueMultiValuedCreateTest extends AbstractScimTest {

    private static final String USER_BY_ID_ENDPOINT = USERS_ENDPOINT + "/1";

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
                      "name": "products",
                      "type": "string",
                      "mutability": "readWrite",
                      "returned": "default",
                      "required": false,
                      "multiValued": true,
                      "caseExact": false
                    },
                    {
                      "name": "displayName",
                      "type": "string",
                      "mutability": "readWrite",
                      "returned": "default",
                      "required": false,
                      "multiValued": false,
                      "caseExact": false
                    }
                  ]
                }
              ]
            }
            """;

    private static final String CREATED_USER_RESOURCE = """
            {
              "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
              "id": "1",
              "userName": "new.user",
              "products": [ "jira-software" ]
            }
            """;

    private static final String NATIVE_SCHEMA_SCRIPT = """
            objectClass("User") {
                attribute("userName") {
                    scim { path attribute("userName") }
                }
                attribute("products") {
                    scim { path attribute("products") }
                }
                attribute("displayName") {
                    scim { path attribute("displayName") }
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

    @Test
    public void creatingWithSingleValueStillProducesArrayForMultiValuedAttribute() {
        stubUserDiscovery(SCHEMAS_RESPONSE);
        wireMockServer.stubFor(post(urlEqualTo(USERS_ENDPOINT))
                .willReturn(aResponse().withStatus(201)
                        .withHeader("Content-Type", "application/scim+json")
                        .withHeader("Location", "http://localhost/scim/Users/1")
                        .withBody(CREATED_USER_RESOURCE)));

        var connector = new ScriptConnector();
        connector.init(new TestConfiguration(wireMockServer.port()));

        // sanity check: the schema itself does mark 'products' as multivalued
        var productsAttribute = connector.schema().findObjectClassInfo("User").getAttributeInfo().stream()
                .filter(a -> a.getName().equals("products"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("products attribute not found in discovered schema"));
        assertTrue(productsAttribute.isMultiValued(), "products must be discovered as multivalued");

        connector.create(new ObjectClass("User"),
                Set.of(
                        AttributeBuilder.build(Name.NAME, "new.user"),
                        AttributeBuilder.build("products", "jira-software")
                ),
                new OperationOptionsBuilder().build());

        var requests = wireMockServer.findAll(postRequestedFor(urlEqualTo(USERS_ENDPOINT)));
        assertEquals(requests.size(), 1, "exactly one create request expected");
        var body = parse(requests.getFirst());

        assertTrue(body.get("products").isArray(),
                "a single value for a multivalued attribute must still be sent as an array");
        assertEquals(body.get("products").size(), 1);
        assertEquals(body.get("products").get(0).asString(), "jira-software");
    }

    @Test(expectedExceptions = InvalidAttributeValueException.class)
    public void creatingWithMultipleValuesForNonMultiValuedAttributeThrows() {
        stubUserDiscovery(SCHEMAS_RESPONSE);

        var connector = new ScriptConnector();
        connector.init(new TestConfiguration(wireMockServer.port()));

        // sanity check: the schema itself does not mark 'displayName' as multivalued
        var displayNameAttribute = connector.schema().findObjectClassInfo("User").getAttributeInfo().stream()
                .filter(a -> a.getName().equals("displayName"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("displayName attribute not found in discovered schema"));
        assertFalse(displayNameAttribute.isMultiValued(), "displayName must not be discovered as multivalued");

        connector.create(new ObjectClass("User"),
                Set.of(
                        AttributeBuilder.build(Name.NAME, "new.user"),
                        AttributeBuilder.build("displayName", Arrays.asList("First Name", "Second Name")),
                        AttributeBuilder.build("products", "jira-software")
                ),
                new OperationOptionsBuilder().build());
    }

    private static ObjectNode parse(LoggedRequest request) {
        return (ObjectNode) MAPPER.readTree(request.getBody());
    }
}
