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
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
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
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Verifies the declarative {@code scim { limitations { supportedFilter(spec) { ... } } } }
 * DSL: the closure form is accepted (evaluated against the empty declarative delegate, no
 * mapping behavior - SCIM filters are translated automatically) and the declared
 * specification limits which filters the SCIM search handler applies - non-matching filters
 * are rejected by the search dispatcher as unsupported instead of being sent to the remote.
 */
public class ScimSearchFilterLimitationsTest extends AbstractScimTest {

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
            {"schemas":["urn:ietf:params:scim:api:messages:2.0:ListResponse"],"totalResults":0,"Resources":[]}
            """;

    private static final String CLOSURE_OPERATIONS_SCRIPT = """
            objectClass("User") {
                search {
                    scim {
                        limitations {
                            supportedFilter(attribute("userName").eq().anySingleValue()) {
                                // declarative only - SCIM filters are translated automatically
                            }
                        }
                    }
                }
            }
            """;

    private static final String SPEC_ONLY_OPERATIONS_SCRIPT = """
            objectClass("User") {
                search {
                    scim {
                        limitations {
                            supportedFilter(attribute("userName").eq().anySingleValue())
                        }
                    }
                }
            }
            """;

    private static final String UNKNOWN_ATTRIBUTE_OPERATIONS_SCRIPT = """
            objectClass("User") {
                search {
                    scim {
                        limitations {
                            supportedFilter(attribute("organization").eq().anySingleValue()) {
                            }
                        }
                    }
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
            if (operationScript != null) {
                builder.loadFromString(operationScript);
            }
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
    public void supportedFilterWithClosureIsAcceptedAndMatchingFilterIsTranslated() {
        var connector = new ScriptConnector(CLOSURE_OPERATIONS_SCRIPT);
        connector.init(new TestConfiguration(wireMockServer.port()));

        var results = new ArrayList<ConnectorObject>();
        connector.executeQuery(new ObjectClass("User"),
                FilterBuilder.equalTo(AttributeBuilder.build(Name.NAME, "jdoe")),
                o -> {
                    results.add((ConnectorObject) o);
                    return true;
                },
                new OperationOptionsBuilder().build());

        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(USERS_ENDPOINT))
                .withQueryParam("filter", equalTo("userName eq \"jdoe\""))).size(), 1);
    }

    @Test
    public void nonMatchingFilterIsRejectedAsUnsupported() {
        var connector = new ScriptConnector(CLOSURE_OPERATIONS_SCRIPT);
        connector.init(new TestConfiguration(wireMockServer.port()));

        var exception = Assert.expectThrows(Exception.class, () -> connector.executeQuery(new ObjectClass("User"),
                FilterBuilder.contains(AttributeBuilder.build(Name.NAME, "do")),
                o -> true,
                new OperationOptionsBuilder().build()));

        assertTrue(exception instanceof ConnectorException);
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
        assertTrue(exception.getCause().getMessage().contains("Unsupported filter"));
        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(USERS_ENDPOINT))).size(), 0);
    }

    @Test
    public void specOnlySupportedFilterKeepsWorking() {
        var connector = new ScriptConnector(SPEC_ONLY_OPERATIONS_SCRIPT);
        connector.init(new TestConfiguration(wireMockServer.port()));

        var results = new ArrayList<ConnectorObject>();
        connector.executeQuery(new ObjectClass("User"),
                FilterBuilder.equalTo(AttributeBuilder.build(Name.NAME, "jdoe")),
                o -> {
                    results.add((ConnectorObject) o);
                    return true;
                },
                new OperationOptionsBuilder().build());

        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(USERS_ENDPOINT))
                .withQueryParam("filter", equalTo("userName eq \"jdoe\""))).size(), 1);
    }

    @Test
    public void unknownFilterAttributeFailsBuildWithDescriptiveError() {
        var connector = new ScriptConnector(UNKNOWN_ATTRIBUTE_OPERATIONS_SCRIPT);
        connector.init(new TestConfiguration(wireMockServer.port()));

        var exception = Assert.expectThrows(Exception.class, () -> connector.executeQuery(
                new ObjectClass("User"), null, o -> true, new OperationOptionsBuilder().build()));

        var cause = firstCause(exception, ConfigurationException.class);
        assertNotNull(cause);
        assertTrue(cause.getMessage().contains("Attribute 'organization' not found in object class 'User'"),
                "Unexpected message: " + cause.getMessage());
        assertTrue(cause.getMessage().contains("when defining a SCIM search limitation"));
        assertTrue(cause.getMessage().contains("Available attributes"));
    }

    private static Throwable firstCause(Throwable throwable, Class<? extends Throwable> type) {
        for (var cause = throwable; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return cause;
            }
        }
        return null;
    }
}
