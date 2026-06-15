/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.support;

import com.evolveum.polygon.scimrest.ClassHandlerConnectorBase;
import com.evolveum.polygon.scimrest.groovy.AbstractGroovyRestConnector;
import com.evolveum.polygon.scimrest.groovy.BaseRestGroovyConnectorConfiguration;
import com.evolveum.polygon.scimrest.groovy.GroovyRestHandlerBuilder;
import com.evolveum.polygon.scimrest.groovy.GroovySchemaLoader;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Generic base for CRUD tests of a Groovy-scripted connector against WireMock,
 * independent of any authorization concern.
 *
 * Provides a minimal Account schema, a default operation script covering search,
 * create and update, WireMock stubs for those endpoints, and helpers invoking the
 * corresponding connector operations. Subclasses may supply a custom operation
 * script via {@link TestConnector#TestConnector(String)} to exercise other DSL
 * directives.
 */
public abstract class AbstractCrudConnectorTest extends WireMockTestSupport {

    protected static final String ACCOUNTS_PATH = "/accounts";
    protected static final String ACCOUNTS_PATTERN = "/accounts/.*";
    protected static final String ACCOUNT_BY_ID_PATH = "/accounts/123";

    protected static final String NATIVE_SCHEMA_SCRIPT = """
            objectClass("Account") {
                attribute("id") { jsonType "string" }
                attribute("name") { jsonType "string" }
            }
            """;

    protected static final String CONNID_SCHEMA_SCRIPT = """
            objectClass("Account") {
                connIdAttribute("UID", "id")
                connIdAttribute("NAME", "name")
            }
            """;

    protected static final String OPERATION_SCRIPT = """
            objectClass("Account") {
                search {
                    endpoint("accounts") { emptyFilterSupported true }
                    endpoint("accounts/{id}") {
                        singleResult()
                        supportedFilter(attribute("id").eq().anySingleValue()) {
                            request.pathParameter("id", value)
                        }
                    }
                }
                create {
                    endpoint("accounts") { }
                }
                update {
                    endpoint(PUT, "accounts/{id}") {
                        request { contentType APPLICATION_JSON }
                    }
                }
            }
            """;

    protected static class SimpleConfig extends BaseTestConfiguration {
        protected SimpleConfig(int port) {
            super(port);
        }

        @Override
        public String getRestTestEndpoint() {
            return null;
        }
    }

    protected static class TestConnector extends AbstractGroovyRestConnector<BaseRestGroovyConnectorConfiguration> {

        private final String nativeSchemaScript;
        private final String connIdSchemaScript;
        private final String operationScript;

        public TestConnector() {
            this(OPERATION_SCRIPT);
        }

        public TestConnector(String operationScript) {
            this(NATIVE_SCHEMA_SCRIPT, CONNID_SCHEMA_SCRIPT, operationScript);
        }

        public TestConnector(String nativeSchemaScript, String connIdSchemaScript, String operationScript) {
            this.nativeSchemaScript = nativeSchemaScript;
            this.connIdSchemaScript = connIdSchemaScript;
            this.operationScript = operationScript;
        }

        @Override protected void initializeSchema(GroovySchemaLoader loader) {
            loader.load(nativeSchemaScript);
            loader.load(connIdSchemaScript);
        }
        @Override protected void initializeAuthorizationHandler(GroovyRestHandlerBuilder builder) { }
        @Override protected void initializeObjectClassHandler(GroovyRestHandlerBuilder builder) {
            builder.loadFromString(operationScript);
        }
    }

    protected TestConnector initConnector(String operationScript) {
        var connector = new TestConnector(operationScript);
        connector.init(new SimpleConfig(wireMockServer.port()));
        return connector;
    }

    protected TestConnector initConnector(String nativeSchemaScript, String connIdSchemaScript, String operationScript) {
        var connector = new TestConnector(nativeSchemaScript, connIdSchemaScript, operationScript);
        connector.init(new SimpleConfig(wireMockServer.port()));
        return connector;
    }

    @BeforeMethod
    public void setUp() throws Exception {
        setUpWireMock();
    }

    @AfterMethod
    public void tearDown() {
        tearDownWireMock();
    }

    protected void stubSearchAccounts() {
        wireMockServer.stubFor(get(urlEqualTo(ACCOUNTS_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));
    }

    protected void stubCreateAccount() {
        wireMockServer.stubFor(post(urlEqualTo(ACCOUNTS_PATH))
                .willReturn(aResponse().withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"123\",\"name\":\"created\"}")));
    }

    protected void stubUpdateAccount() {
        wireMockServer.stubFor(get(urlEqualTo(ACCOUNT_BY_ID_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"123\",\"name\":\"test-account\"}")));
        wireMockServer.stubFor(put(urlPathMatching(ACCOUNTS_PATTERN))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"123\",\"name\":\"updated\"}")));
    }

    protected void searchAccounts(ClassHandlerConnectorBase connector) {
        connector.executeQuery(new ObjectClass("Account"), null, r -> true, new OperationOptionsBuilder().build());
    }

    protected List<ConnectorObject> search(ClassHandlerConnectorBase connector, Filter filter) {
        var results = new ArrayList<ConnectorObject>();
        connector.executeQuery(new ObjectClass("Account"), filter,
                o -> { results.add(o); return true; },
                new OperationOptionsBuilder().build());
        return results;
    }

    protected void createAccount(ClassHandlerConnectorBase connector) {
        connector.create(new ObjectClass("Account"),
                Set.of(AttributeBuilder.build("name", "test")),
                new OperationOptionsBuilder().build());
    }

    protected void updateAccount(ClassHandlerConnectorBase connector) {
        connector.updateDelta(new ObjectClass("Account"),
                new Uid("123"),
                Set.of(AttributeDeltaBuilder.build(Name.NAME, List.of("updated"))),
                new OperationOptionsBuilder().build());
    }
}
