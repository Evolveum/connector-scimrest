/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.crud;

import com.evolveum.polygon.conndev.groovy.GroovySchemaLoader;
import com.evolveum.polygon.conndev.groovy.GroovyScriptLoader;
import com.evolveum.polygon.conndev.spi.ClassHandlerConnectorBase;
import com.evolveum.polygon.scimrest.groovy.connector.AbstractGroovyRestConnector;
import com.evolveum.polygon.scimrest.groovy.handler.GroovyRestHandlerBuilder;
import com.evolveum.polygon.scimrest.support.AbstractCrudConnectorTest;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.*;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class LookupSupportTest extends AbstractCrudConnectorTest {

    private static final String WORKPACKAGES_PATH = "/workpackages";
    private static final String STATUSES_PATH = "/statuses";

    private static final String STATUS_TODO_HREF = "http://scimrest.test/statuses/1";
    private static final String STATUS_IN_PROGRESS_HREF = "http://scimrest.test/statuses/2";
    private static final String UNKNOWN_STATUS_HREF = "http://scimrest.test/statuses/999";

    private static final String NATIVE_SCHEMA_SCRIPT = """
            objectClass("WorkPackage") {
                attribute("id") { jsonType "string" }
                attribute("name") { jsonType "string" }
                attribute("subject") { jsonType "string" }
                attribute("status") {
                    readable true
                    creatable true
                    updatable true
                    json {
                        type("string")
                        path attribute("_links").child("status").child("href")
                        lookup {
                            objectClass("Status") {
                                serialize "href"
                                deserialize "__NAME__"
                            }
                        }
                    }
                }
            }
            objectClass("Status") {
                cached true
                attribute("name") { jsonType "string" }
                attribute("id") { jsonType "string" }
                attribute("href") {
                    json {
                        type("string")
                        path attribute("_links").child("self").child("href")
                    }
                }
            }
            """;

    private static final String CONNID_SCHEMA_SCRIPT = """
            objectClass("WorkPackage") {
                connIdAttribute("UID", "id")
                connIdAttribute("NAME", "name")
            }
            objectClass("Status") {
                connIdAttribute("UID", "id")
                connIdAttribute("NAME", "name")
            }
            """;

    private static final String OPERATION_SCRIPT = """
             objectClass("WorkPackage") {
                 search {
                     endpoint("workpackages") {
                         responseFormat JSON_ARRAY
                         emptyFilterSupported true
                     }
                     endpoint("workpackages/{id}") {
                         responseFormat JSON_OBJECT
                         singleResult()
                         supportedFilter(attribute("id").eq().anySingleValue()) {
                             request.pathParameter("id", value)
                         }
                     }
                 }
                 create {
                     endpoint(POST, "workpackages") {
                         request { contentType APPLICATION_JSON }
                     }
                 }
                 update {
                     endpoint(PUT, "workpackages/{id}") {
                         request { contentType APPLICATION_JSON }
                     }
                 }
             }
             objectClass("Status") {
                 search {
                     endpoint("statuses") {
                         responseFormat JSON_ARRAY
                         emptyFilterSupported true
                     }
                 }
             }
            \s""";

    private static final String STATUSES_BODY = """
            [{"id":"11","name":"To Do","_links":{"self":{"href":"%s"}}},
             {"id":"21","name":"In Progress","_links":{"self":{"href":"%s"}}}]
            """.formatted(STATUS_TODO_HREF, STATUS_IN_PROGRESS_HREF);

    @Test
    public void searchResolvesNativeLookupValueToUserFacingValue() {
        stubStatuses(STATUSES_BODY);
        wireMockServer.stubFor(get(urlEqualTo(WORKPACKAGES_PATH))
                .willReturn(okJson(buildWorkPackagesBody(STATUS_TODO_HREF))));

        var results = searchWorkPackages(connector());

        assertEquals(results.size(), 1);
        assertEquals(results.get(0).getAttributeByName("subject").getValue().get(0), "first");
        assertEquals(results.get(0).getAttributeByName("status").getValue().get(0), "To Do");
        assertEquals(wireMockServer.findAll(getRequestedFor(urlEqualTo(STATUSES_PATH))).size(), 1);
    }

    @Test
    public void searchPassesUnknownNativeValueThrough() {
        stubStatuses("""
                [{"id":"1001","name":"To Do","_links":{"self":{"href":"%s"}}}]
                """.formatted(STATUS_TODO_HREF));
        wireMockServer.stubFor(get(urlEqualTo(WORKPACKAGES_PATH))
                .willReturn(okJson(buildWorkPackagesBody(UNKNOWN_STATUS_HREF))));

        var results = searchWorkPackages(connector());

        assertEquals(results.size(), 1);
        assertEquals(results.get(0).getAttributeByName("status").getValue().get(0), UNKNOWN_STATUS_HREF);
    }

    @Test
    public void lookupTableIsLoadedOnlyOncePerConnectorInstance() {
        stubStatuses(STATUSES_BODY);
        wireMockServer.stubFor(get(urlEqualTo(WORKPACKAGES_PATH))
                .willReturn(okJson(buildWorkPackagesBody(STATUS_TODO_HREF))));

        var connector = connector();
        searchWorkPackages(connector);
        searchWorkPackages(connector);

        assertEquals(wireMockServer.findAll(getRequestedFor(urlEqualTo(STATUSES_PATH))).size(), 1);
        assertEquals(wireMockServer.findAll(getRequestedFor(urlEqualTo(WORKPACKAGES_PATH))).size(), 2);
    }

    @Test
    public void createResolvesUserFacingValueToNativeValue() {
        stubStatuses(STATUSES_BODY);
        wireMockServer.stubFor(post(urlEqualTo(WORKPACKAGES_PATH))
                .willReturn(okJson("{\"id\":\"1\",\"name\":\"created\",\"subject\":\"created\"}")));

        connector().create(new ObjectClass("WorkPackage"),
                Set.of(AttributeBuilder.build("status", "In Progress")),
                new OperationOptionsBuilder().build());

        var requests = wireMockServer.findAll(postRequestedFor(urlEqualTo(WORKPACKAGES_PATH)));
        assertEquals(requests.size(), 1);
        var body = requests.get(0).getBodyAsString();
        assertTrue(body.contains(STATUS_IN_PROGRESS_HREF),
                "Expected the native href in the request body, but was: " + body);
        assertFalse(body.contains("In Progress"),
                "Expected the human-facing value not to leak into the request body, but was: " + body);
    }

    @Test(expectedExceptions = ConnectorException.class)
    public void createFailsOnUnknownUserFacingValue() {
        stubStatuses(STATUSES_BODY);

        connector().create(new ObjectClass("WorkPackage"),
                Set.of(AttributeBuilder.build("status", "No Such Status")),
                new OperationOptionsBuilder().build());
    }

    @Test(expectedExceptions = ConfigurationException.class)
    public void createFailsWhenCachedClassContainsDuplicateLookupValues() {
        stubStatuses("""
                [{"id":"12","name":"To Do","_links":{"self":{"href":"%s"}}},
                 {"id":"31","name":"To Do","_links":{"self":{"href":"%s"}}}]
                """.formatted(STATUS_TODO_HREF, STATUS_IN_PROGRESS_HREF));

        connector().create(new ObjectClass("WorkPackage"),
                Set.of(AttributeBuilder.build("status", "To Do")),
                new OperationOptionsBuilder().build());
    }

    @Test
    public void updateResolvesUserFacingValueToNativeValue() {
        stubStatuses(STATUSES_BODY);
        wireMockServer.stubFor(get(urlEqualTo("/workpackages/1"))
                .willReturn(okJson("""
                        {"id":"1","name":"TEST","subject":"first",
                         "_links":{"status":{"href":"%s"}}}
                        """.formatted(STATUS_TODO_HREF))));
        wireMockServer.stubFor(put(urlEqualTo("/workpackages/1"))
                .willReturn(okJson("""
                        {"id":"1","name":"TEST","subject":"first",
                         "_links":{"status":{"href":"%s"}}}
                        """.formatted(STATUS_IN_PROGRESS_HREF))));

        connector().updateDelta(new ObjectClass("WorkPackage"),
                new Uid("1"),
                Set.of(AttributeDeltaBuilder.build("status",
                        List.of("In Progress"))),
                new OperationOptionsBuilder().build());

        var requests = wireMockServer.findAll(putRequestedFor(urlEqualTo("/workpackages/1")));
        assertEquals(requests.size(), 1);
        var body = requests.get(0).getBodyAsString();
        assertTrue(body.contains(STATUS_IN_PROGRESS_HREF),
                "Expected the native href in the PUT body, but was: " + body);
        assertFalse(body.contains("In Progress"),
                "Expected the human-facing value not to leak into the PUT body, but was: " + body);
    }

    @Test(expectedExceptions = ConnectorException.class)
    public void updateFailsOnUnknownUserFacingValue() {
        stubStatuses(STATUSES_BODY);
        wireMockServer.stubFor(get(urlEqualTo("/workpackages/1"))
                .willReturn(okJson("""
                        {"id":"1","name":"TEST","subject":"first",
                         "_links":{"status":{"href":"%s"}}}
                        """.formatted(STATUS_TODO_HREF))));

        connector().updateDelta(new ObjectClass("WorkPackage"),
                new Uid("1"),
                Set.of(AttributeDeltaBuilder.build("status",
                        List.of("No Such Status"))),
                new OperationOptionsBuilder().build());
    }

    private ClassHandlerConnectorBase connector() {
        var connector = new LookupTestConnector(NATIVE_SCHEMA_SCRIPT, CONNID_SCHEMA_SCRIPT, OPERATION_SCRIPT);
        connector.init(new SimpleConfig(wireMockServer.port()));
        return connector;
    }

    private List<ConnectorObject> searchWorkPackages(ClassHandlerConnectorBase connector) {
        var results = new ArrayList<ConnectorObject>();
        connector.executeQuery(new ObjectClass("WorkPackage"), null,
                o -> {
                    results.add(o);
                    return true;
                },
                new OperationOptionsBuilder().build());
        return results;
    }

    private void stubStatuses(String body) {
        wireMockServer.stubFor(get(urlEqualTo(STATUSES_PATH)).willReturn(okJson(body)));
    }

    private static String buildWorkPackagesBody(String statusHref) {
        return """
                [{"id":"1","name":"TEST","subject":"first","_links":{"status":{"href":"%s"}}}]
                """.formatted(statusHref);
    }

    private static final class LookupTestConnector extends AbstractGroovyRestConnector {

        private final String nativeSchemaScript;
        private final String connIdSchemaScript;
        private final String operationScript;

        LookupTestConnector(String nativeSchemaScript, String connIdSchemaScript, String operationScript) {
            super(false);
            this.nativeSchemaScript = nativeSchemaScript;
            this.connIdSchemaScript = connIdSchemaScript;
            this.operationScript = operationScript;
        }

        @Override
        protected void initializeSchema(GroovySchemaLoader loader) {
            loader.load(nativeSchemaScript);
            loader.load(connIdSchemaScript);
        }

        @Override
        protected void initializeAuthorizationHandler(GroovyRestHandlerBuilder builder) {
        }

        @Override
        protected void initializeObjectClassHandler(GroovyScriptLoader builder) {
            builder.loadFromString(operationScript);
        }
    }
}