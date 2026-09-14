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
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.AttributeDeltaBuilder;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.Uid;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.assertEquals;

/**
 * Verifies that the SCIM update operation defaults to the SCIM PATCH strategy (RFC 7644 section
 * 3.5.2): a {@code connector.updateDelta(...)} call is translated into one or more
 * {@code PATCH /Users/{id}} requests carrying a {@code PatchOp} body, without reading the
 * original resource state first.
 */
public class ScimPatchUpdateTest extends WireMockTestSupport {

    private static final String SCIM_BASE_PATH = "/scim";
    private static final String SCHEMAS_ENDPOINT = SCIM_BASE_PATH + "/Schemas";
    private static final String RESOURCE_TYPES_ENDPOINT = SCIM_BASE_PATH + "/ResourceTypes";
    private static final String USER_BY_ID_PATH = SCIM_BASE_PATH + "/Users/123";

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
                    { "name": "userName", "type": "string",  "mutability": "readWrite", "multiValued": false },
                    { "name": "active",   "type": "boolean", "mutability": "readWrite", "multiValued": false },
                    { "name": "emails",   "type": "string",  "mutability": "readWrite", "multiValued": true }
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

    private static final String USER_RESPONSE = """
            {
              "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
              "id": "123",
              "userName": "jdoe",
              "active": false
            }
            """;

    private static final String NATIVE_SCHEMA_SCRIPT = """
            objectClass("User") {
                attribute("userName") { scim { path attribute("userName") } }
                attribute("active")   { scim { path attribute("active") } }
                attribute("emails")   { scim { path attribute("emails") } }
            }
            """;

    private static final String CONNID_SCHEMA_SCRIPT = """
            objectClass("User") {
                connIdAttribute("UID", "id");
                connIdAttribute("NAME", "userName");
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

    private static class ScriptConnector extends AbstractGroovyRestConnector<TestConfiguration> {

        private final String operationScript;

        ScriptConnector(String operationScript) {
            this.operationScript = operationScript;
        }

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
            if (operationScript != null) {
                builder.loadFromString(operationScript);
            }
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

    private void stubUserRead() {
        wireMockServer.stubFor(get(urlEqualTo(USER_BY_ID_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/scim+json")
                        .withBody(USER_RESPONSE)));
    }

    private ScriptConnector initConnector(String operationScript) {
        var connector = new ScriptConnector(operationScript);
        connector.init(new TestConfiguration(wireMockServer.port()));
        return connector;
    }

    @Test
    public void defaultUpdateSendsScimPatchReplace() {
        stubDiscovery();
        stubUserRead();
        wireMockServer.stubFor(patch(urlEqualTo(USER_BY_ID_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/scim+json")
                        .withBody(USER_RESPONSE)));

        var connector = initConnector(null);
        connector.updateDelta(new ObjectClass("User"), new Uid("123"),
                Set.of(AttributeDeltaBuilder.build("active", List.of(true))),
                new OperationOptionsBuilder().build());

        var patchRequests = wireMockServer.findAll(patchRequestedFor(urlEqualTo(USER_BY_ID_PATH))
                .withHeader("Content-Type", containing("scim+json"))
                .withHeader("Authorization", equalTo("Bearer test-token"))
                .withRequestBody(matchingJsonPath("$.schemas[0]",
                        equalTo("urn:ietf:params:scim:api:messages:2.0:PatchOp")))
                .withRequestBody(matchingJsonPath("$.Operations[0].op", equalTo("replace")))
                .withRequestBody(matchingJsonPath("$.Operations[0].path", equalTo("active")))
                .withRequestBody(matchingJsonPath("$.Operations[0].value", equalTo("true"))));
        assertEquals(patchRequests.size(), 1);
        // PATCH must not read the original state first
        assertEquals(wireMockServer.findAll(getRequestedFor(urlEqualTo(USER_BY_ID_PATH))).size(), 0);
    }

    @Test
    public void patchMaxPerRequestSplitsMultiValueAdd() {
        stubDiscovery();
        stubUserRead();
        wireMockServer.stubFor(patch(urlPathMatching(SCIM_BASE_PATH + "/Users/123"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/scim+json")
                        .withBody(USER_RESPONSE)));

        var connector = initConnector("""
                objectClass("User") {
                    update {
                        scim {
                            patch {
                                supportedAttribute("emails") {
                                    limitations {
                                        maxPerRequest 2
                                    }
                                }
                            }
                        }
                    }
                }
                """);
        var emails = List.of("a@x.com", "b@x.com", "c@x.com", "d@x.com", "e@x.com");
        var delta = new AttributeDeltaBuilder().setName("emails").addValueToAdd(emails.toArray()).build();
        connector.updateDelta(new ObjectClass("User"), new Uid("123"),
                Set.of(delta), new OperationOptionsBuilder().build());

        // 5 values with maxPerRequest 2 -> 3 PATCH requests (2 + 2 + 1)
        var patchRequests = wireMockServer.findAll(patchRequestedFor(urlEqualTo(USER_BY_ID_PATH))
                .withRequestBody(matchingJsonPath("$.Operations[0].op", equalTo("add"))));
        assertEquals(patchRequests.size(), 3);
    }
}
