/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.auth.method.awssignature;

import com.evolveum.polygon.scimrest.config.ScimClientConfiguration;
import com.evolveum.polygon.scimrest.support.WireMockTestSupport;
import com.evolveum.polygon.scimrest.groovy.AbstractGroovyRestConnector;
import com.evolveum.polygon.scimrest.groovy.BaseGroovyConnectorConfiguration;
import com.evolveum.polygon.scimrest.groovy.GroovyRestHandlerBuilder;
import com.evolveum.polygon.scimrest.groovy.GroovySchemaLoader;
import org.identityconnectors.common.security.GuardedString;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.*;

public class ScimAwsSignatureTests extends WireMockTestSupport {

    private static final String SCIM_BASE    = "/scim";
    private static final String SCHEMAS_EP   = SCIM_BASE + "/Schemas";
    private static final String RESOURCES_EP = SCIM_BASE + "/ResourceTypes";
    private static final String ACCESS_KEY   = "SCIM-ACCESS-KEY";
    private static final String SECRET_KEY   = "scim-secret-key-value";
    private static final String REGION       = "eu-west-1";
    private static final String SERVICE      = "execute-api";

    private static final String EMPTY_LIST = """
            {"schemas":["urn:ietf:params:scim:api:messages:2.0:ListResponse"],"totalResults":0,"Resources":[]}
            """;

    @BeforeMethod
    public void setUp() {
        setUpWireMock();
    }

    @AfterMethod
    public void tearDown() {
        tearDownWireMock();
    }

    @Test
    public void testScimRequestsAreSigned() {
        stubScimEndpoints();

        createScimConnector(null).schema();

        wireMockServer.verify(getRequestedFor(urlPathEqualTo(SCHEMAS_EP))
                .withHeader("Authorization", matching("AWS4-HMAC-SHA256 Credential=" + ACCESS_KEY + "/.*"))
                .withHeader("x-amz-date", matching("\\d{8}T\\d{6}Z")));
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 2);
    }

    @Test
    public void testAllScimRequestsAreSigned() {
        stubScimEndpoints();

        createScimConnector(null).schema();

        wireMockServer.verify(getRequestedFor(urlPathEqualTo(SCHEMAS_EP))
                .withHeader("Authorization", matching("AWS4-HMAC-SHA256 .*")));
        wireMockServer.verify(getRequestedFor(urlPathEqualTo(RESOURCES_EP))
                .withHeader("Authorization", matching("AWS4-HMAC-SHA256 .*")));
    }

    @Test
    public void testBeforeSignHookRunsBeforeSigningForScim() {
        stubScimEndpoints();

        var script = """
                authentication {
                    scim {
                        awsSignature {
                            beforeSign {
                                signHeader("x-custom")
                                request.header("x-custom", "scim-custom")
                            }
                        }
                    }
                }
                """;

        createScimConnector(script).schema();

        var requests = wireMockServer.findAll(getRequestedFor(urlPathEqualTo(SCHEMAS_EP)));
        assertFalse(requests.isEmpty());
        assertEquals(requests.get(0).getHeader("x-custom"), "scim-custom",
                "beforeSign hook must run before signing");
        String auth = requests.get(0).getHeader("Authorization");
        assertNotNull(auth, "AWS signing must always run after beforeSign hook");
        assertTrue(auth.contains("x-custom"), "signHeader must include x-custom in SignedHeaders");
    }

    // --- infrastructure ---

    private void stubScimEndpoints() {
        wireMockServer.stubFor(get(urlPathEqualTo(SCHEMAS_EP))
                .withHeader("Authorization", matching("AWS4-HMAC-SHA256 .*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/scim+json")
                        .withBody(EMPTY_LIST)));
        wireMockServer.stubFor(get(urlPathEqualTo(RESOURCES_EP))
                .withHeader("Authorization", matching("AWS4-HMAC-SHA256 .*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/scim+json")
                        .withBody(EMPTY_LIST)));
    }

    private AbstractGroovyRestConnector<?> createScimConnector(String script) {
        var config = new ScimAwsConfig(wireMockServer.port(), ACCESS_KEY,
                new GuardedString(SECRET_KEY.toCharArray()), REGION, SERVICE);
        var connector = new ScimAwsConnector(script);
        connector.init(config);
        return connector;
    }

    class ScimAwsConfig extends BaseGroovyConnectorConfiguration
            implements ScimClientConfiguration.AwsSignatureAuthorization {

        private final int port;
        private final String accessKey;
        private final GuardedString secretKey;
        private final String region;
        private final String service;

        ScimAwsConfig(int port, String accessKey, GuardedString secretKey, String region, String service) {
            this.port = port;
            this.accessKey = accessKey;
            this.secretKey = secretKey;
            this.region = region;
            this.service = service;
        }

        @Override public String getScimBaseUrl()               { return "http://localhost:" + port + SCIM_BASE; }
        @Override public String getScimAwsAccessKey()          { return accessKey; }
        @Override public GuardedString getScimAwsSecretKey()   { return secretKey; }
        @Override public String getScimAwsRegion()             { return region; }
        @Override public String getScimAwsService()            { return service; }
        @Override public GuardedString getScimAwsSessionToken() { return null; }
    }

    static class ScimAwsConnector extends AbstractGroovyRestConnector<BaseGroovyConnectorConfiguration> {
        private final String script;

        ScimAwsConnector(String script) {
            super(false);
            this.script = script;
        }

        @Override protected void initializeSchema(GroovySchemaLoader loader) {}

        @Override
        protected void initializeAuthorizationHandler(GroovyRestHandlerBuilder builder) {
            if (script != null) builder.loadFromString(script);
        }

        @Override protected void initializeObjectClassHandler(GroovyRestHandlerBuilder builder) {}
    }
}
