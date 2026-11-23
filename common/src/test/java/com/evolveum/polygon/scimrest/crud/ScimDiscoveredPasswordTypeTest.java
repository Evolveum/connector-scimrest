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
import com.evolveum.polygon.scimrest.schema.ScimAttributeMapping;
import com.evolveum.polygon.scimrest.support.AbstractScimTest;
import org.identityconnectors.common.security.GuardedString;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

/**
 * Regression for work package 13476: a discovered SCIM User {@code password} attribute
 * (a well-known schema attribute) must be exposed to ConnId as a {@link GuardedString}
 * out of the box — previously it defaulted to {@code String}, so the password could end up
 * in operation logs in the clear unless the connector author explicitly declared
 * {@code connId { type GuardedString }} for it.
 * <p>
 * The test shape mirrors {@link ScimDiscoveredBooleanTypeTest}: a hand-written native schema
 * maps the attribute via {@code scim { path attribute("password") }} with no explicit ConnId
 * type, and live SCIM discovery ({@code /Schemas}) reports the standard User schema.
 */
public class ScimDiscoveredPasswordTypeTest extends AbstractScimTest {

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
                      "name": "password",
                      "type": "string",
                      "mutability": "writeOnly",
                      "returned": "never",
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

    private static final String NATIVE_SCHEMA_SCRIPT = """
            objectClass("User") {
                attribute("userName") {
                    scim { path attribute("userName") }
                }
                attribute("password") {
                    scim { path attribute("password") }
                }
            }
            """;

    private static final String NATIVE_SCHEMA_SCRIPT_WITH_STRING_PASSWORD = """
            objectClass("User") {
                attribute("userName") {
                    scim { path attribute("userName") }
                }
                attribute("password") {
                    connId { type String.class }
                    scim { path attribute("password") }
                }
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

    private static class ScriptConnector extends AbstractGroovyRestConnector {
        private final String nativeSchemaScript;

        ScriptConnector(String nativeSchemaScript) {
            this.nativeSchemaScript = nativeSchemaScript;
        }

        @Override
        protected void initializeSchema(GroovySchemaLoader loader) {
            loader.load(nativeSchemaScript);
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
    public void discoveredPasswordIsExposedAsGuardedStringOutOfTheBox() {
        stubUserDiscovery(SCHEMAS_RESPONSE);

        var connector = new ScriptConnector(NATIVE_SCHEMA_SCRIPT);
        connector.init(new TestConfiguration(wireMockServer.port()));

        var schema = connector.schema();
        var userObjectClass = schema.findObjectClassInfo("User");
        assertNotNull(userObjectClass, "User object class must be discovered");

        // the ConnId name of userName is __NAME__ (mapped to the built-in), so look up by native name
        var passwordAttribute = userObjectClass.getAttributeInfo().stream()
                .filter(a -> "password".equals(a.getNativeName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("password attribute not found in discovered schema"));

        assertEquals(passwordAttribute.getType(), GuardedString.class);

        // a regular string attribute is unaffected
        var userNameAttribute = userObjectClass.getAttributeInfo().stream()
                .filter(a -> "userName".equals(a.getNativeName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("userName attribute not found in discovered schema"));
        assertEquals(userNameAttribute.getType(), String.class);
    }

    @Test
    public void passwordValueIsConvertedToGuardedStringAndBack() {
        stubUserDiscovery(SCHEMAS_RESPONSE);

        var connector = new ScriptConnector(NATIVE_SCHEMA_SCRIPT);
        connector.init(new TestConfiguration(wireMockServer.port()));
        connector.schema(); // triggers schema initialization (sets the built RestSchema on the context)

        var userObjectClass = connector.context().schema().objectClass("User");
        var scimMapping = userObjectClass.attributeFromProtocolName("password").scim();
        assertNotNull(scimMapping, "password attribute must carry a SCIM mapping");
        assertEquals(scimMapping.connIdType(), GuardedString.class);

        var mapper = new ObjectMapper();
        var root = mapper.createObjectNode();
        root.put("password", "s3cret");

        // wire -> ConnId: the value arrives as a GuardedString
        var connIdValue = scimMapping.singleValueFromObject(root);
        assertEquals(connIdValue, new GuardedString("s3cret".toCharArray()));

        // ConnId -> wire: the protocol still carries the plain JSON string
        var wireValues = scimMapping.wireValues(List.of(new GuardedString("s3cret".toCharArray())));
        assertEquals(wireValues.size(), 1);
        assertEquals(wireValues.getFirst().asText(), "s3cret");
    }

    /**
     * The out-of-the-box GuardedString is a detected default — an explicit
     * {@code connId { type ... }} declaration by the connector author still takes
     * precedence (here: downgraded back to String), and the mapping bridges the value
     * conversion accordingly.
     */
    @Test
    public void explicitConnIdTypeStillOverridesDetectedGuardedString() {
        stubUserDiscovery(SCHEMAS_RESPONSE);

        var connector = new ScriptConnector(NATIVE_SCHEMA_SCRIPT_WITH_STRING_PASSWORD);
        connector.init(new TestConfiguration(wireMockServer.port()));

        var schema = connector.schema();
        var userObjectClass = schema.findObjectClassInfo("User");

        var passwordAttribute = userObjectClass.getAttributeInfo().stream()
                .filter(a -> "password".equals(a.getNativeName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("password attribute not found in discovered schema"));
        assertEquals(passwordAttribute.getType(), String.class);

        var scimMapping = connector.context().schema()
                .objectClass("User")
                .attributeFromProtocolName("password")
                .scim();
        assertNotNull(scimMapping);
        assertEquals(scimMapping.connIdType(), String.class);

        var mapper = new ObjectMapper();
        var root = mapper.createObjectNode();
        root.put("password", "s3cret");
        assertEquals(scimMapping.singleValueFromObject(root), "s3cret");
    }
}
