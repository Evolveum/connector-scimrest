/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.auth.method.awssignature;

import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.support.WireMockTestSupport;
import com.evolveum.polygon.scimrest.groovy.AbstractGroovyRestConnector;
import com.evolveum.polygon.scimrest.groovy.BaseRestGroovyConnectorConfiguration;
import com.evolveum.polygon.scimrest.groovy.GroovyRestHandlerBuilder;
import com.evolveum.polygon.scimrest.groovy.GroovySchemaLoader;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.*;

public class AwsSignatureTests extends WireMockTestSupport {

    private static final String API_ENDPOINT = "/api/resource";
    private static final String ACCESS_KEY   = "AKIAIOSFODNN7EXAMPLE";
    private static final String SECRET_KEY   = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY";
    private static final String REGION       = "us-east-1";
    private static final String SERVICE      = "execute-api";

    @BeforeMethod
    public void setUp() {
        setUpWireMock();
    }

    @AfterMethod
    public void tearDown() {
        tearDownWireMock();
    }

    @Test
    public void testAuthorizationHeaderIsSigned() {
        wireMockServer.stubFor(get(urlPathEqualTo(API_ENDPOINT))
                .withHeader("Authorization", matching("AWS4-HMAC-SHA256 Credential=.*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        createConnector(null).test();

        wireMockServer.verify(getRequestedFor(urlPathEqualTo(API_ENDPOINT))
                .withHeader("Authorization", matching("AWS4-HMAC-SHA256 Credential=" + ACCESS_KEY + "/.*")));
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 1);
    }

    @Test
    public void testAuthorizationHeaderStructure() {
        wireMockServer.stubFor(get(urlPathEqualTo(API_ENDPOINT))
                .withHeader("Authorization", matching("AWS4-HMAC-SHA256 .*"))
                .willReturn(aResponse().withStatus(200).withBody("{}")));

        createConnector(null).test();

        var requests = wireMockServer.findAll(getRequestedFor(urlPathEqualTo(API_ENDPOINT)));
        assertFalse(requests.isEmpty());
        String auth = requests.get(0).getHeader("Authorization");
        assertTrue(auth.startsWith("AWS4-HMAC-SHA256 "), "Must use SigV4 algorithm");
        assertTrue(auth.contains("Credential=" + ACCESS_KEY + "/"), "Must include access key in credential");
        assertTrue(auth.contains("/" + REGION + "/"), "Must include region in credential scope");
        assertTrue(auth.contains("/" + SERVICE + "/"), "Must include service in credential scope");
        assertTrue(auth.contains("/aws4_request"), "Must include aws4_request terminator");
        assertTrue(auth.contains("SignedHeaders="), "Must include SignedHeaders");
        assertTrue(auth.contains("Signature="), "Must include Signature");
    }

    @Test
    public void testAmzDateHeaderIsPresent() {
        wireMockServer.stubFor(get(urlPathEqualTo(API_ENDPOINT))
                .withHeader("x-amz-date", matching("\\d{8}T\\d{6}Z"))
                .willReturn(aResponse().withStatus(200).withBody("{}")));

        createConnector(null).test();

        wireMockServer.verify(getRequestedFor(urlPathEqualTo(API_ENDPOINT))
                .withHeader("x-amz-date", matching("\\d{8}T\\d{6}Z")));
    }

    @Test
    public void testSessionTokenHeaderAddedWhenConfigured() {
        wireMockServer.stubFor(get(urlPathEqualTo(API_ENDPOINT))
                .withHeader("x-amz-security-token", equalTo("my-session-token"))
                .willReturn(aResponse().withStatus(200).withBody("{}")));

        createConnector(null, "my-session-token").test();

        wireMockServer.verify(getRequestedFor(urlPathEqualTo(API_ENDPOINT))
                .withHeader("x-amz-security-token", equalTo("my-session-token")));
    }

    @Test
    public void testSessionTokenHeaderAbsentWhenNotConfigured() {
        wireMockServer.stubFor(get(urlPathEqualTo(API_ENDPOINT))
                .willReturn(aResponse().withStatus(200).withBody("{}")));

        createConnector(null).test();

        var requests = wireMockServer.findAll(getRequestedFor(urlPathEqualTo(API_ENDPOINT)));
        assertFalse(requests.isEmpty());
        assertNull(requests.get(0).getHeader("x-amz-security-token"),
                "x-amz-security-token should not be present when session token is not configured");
    }

    @Test
    public void testContentSha256HeaderIsPresent() {
        wireMockServer.stubFor(get(urlPathEqualTo(API_ENDPOINT))
                .withHeader("x-amz-content-sha256", matching("[0-9a-f]{64}"))
                .willReturn(aResponse().withStatus(200).withBody("{}")));

        createConnector(null).test();

        var requests = wireMockServer.findAll(getRequestedFor(urlPathEqualTo(API_ENDPOINT)));
        assertFalse(requests.isEmpty());
        String contentSha256 = requests.get(0).getHeader("x-amz-content-sha256");
        assertNotNull(contentSha256, "x-amz-content-sha256 must be present");
        // empty body hash
        assertEquals(contentSha256, "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    public void testSessionTokenIncludedInSignedHeaders() {
        wireMockServer.stubFor(get(urlPathEqualTo(API_ENDPOINT))
                .willReturn(aResponse().withStatus(200).withBody("{}")));

        createConnector(null, "some-token").test();

        String auth = wireMockServer.findAll(getRequestedFor(urlPathEqualTo(API_ENDPOINT)))
                .get(0).getHeader("Authorization");
        assertTrue(auth.contains("x-amz-security-token"), "x-amz-security-token must appear in SignedHeaders when present");
    }

    @Test
    public void testSignedHeadersContainHostDateAndContentSha256() {
        wireMockServer.stubFor(get(urlPathEqualTo(API_ENDPOINT))
                .willReturn(aResponse().withStatus(200).withBody("{}")));

        createConnector(null).test();

        String auth = wireMockServer.findAll(getRequestedFor(urlPathEqualTo(API_ENDPOINT)))
                .get(0).getHeader("Authorization");
        String signedHeaders = auth.replaceAll(".*SignedHeaders=([^,]+).*", "$1");
        assertEquals(signedHeaders, "host;x-amz-content-sha256;x-amz-date",
                "SignedHeaders must be in alphabetical order");
    }

    @Test
    public void testSignedHeadersWithSessionTokenAreAlphabetical() {
        wireMockServer.stubFor(get(urlPathEqualTo(API_ENDPOINT))
                .willReturn(aResponse().withStatus(200).withBody("{}")));

        createConnector(null, "some-token").test();

        String auth = wireMockServer.findAll(getRequestedFor(urlPathEqualTo(API_ENDPOINT)))
                .get(0).getHeader("Authorization");
        String signedHeaders = auth.replaceAll(".*SignedHeaders=([^,]+).*", "$1");
        assertEquals(signedHeaders, "host;x-amz-content-sha256;x-amz-date;x-amz-security-token",
                "SignedHeaders must be in alphabetical order");
    }

    @Test
    public void testBeforeSignHookRunsBeforeSigning() {
        wireMockServer.stubFor(get(urlPathEqualTo(API_ENDPOINT))
                .willReturn(aResponse().withStatus(200).withBody("{}")));

        var script = """
                authentication {
                    rest {
                        awsSignature {
                            beforeSign {
                                signHeader("x-custom")
                                request.header("x-custom", "custom-value")
                            }
                        }
                    }
                }
                """;

        createConnector(script).test();

        var requests = wireMockServer.findAll(getRequestedFor(urlPathEqualTo(API_ENDPOINT)));
        assertFalse(requests.isEmpty());
        assertEquals(requests.get(0).getHeader("x-custom"), "custom-value",
                "beforeSign hook must run before signing");
        String auth = requests.get(0).getHeader("Authorization");
        assertNotNull(auth, "AWS signing must always run after beforeSign hook");
        assertTrue(auth.contains("x-custom"), "signHeader must include x-custom in SignedHeaders");
    }

    @Test
    public void testDifferentRequestsGetDifferentDates() throws InterruptedException {
        wireMockServer.stubFor(get(urlPathEqualTo(API_ENDPOINT))
                .willReturn(aResponse().withStatus(200).withBody("{}")));

        var connector = createConnector(null);
        connector.test();
        Thread.sleep(1100); // ensure new second
        connector.test();

        var requests = wireMockServer.findAll(getRequestedFor(urlPathEqualTo(API_ENDPOINT)));
        assertEquals(requests.size(), 2);
        // Both requests must have a valid x-amz-date header
        assertTrue(requests.get(0).getHeader("x-amz-date").matches("\\d{8}T\\d{6}Z"));
        assertTrue(requests.get(1).getHeader("x-amz-date").matches("\\d{8}T\\d{6}Z"));
    }

    // --- infrastructure ---

    private AbstractGroovyRestConnector<?> createConnector(String script) {
        return createConnector(script, null);
    }

    private AbstractGroovyRestConnector<?> createConnector(String script, String sessionToken) {
        var config = new AwsTestConfig(wireMockServer.port(), API_ENDPOINT,
                ACCESS_KEY, new GuardedString(SECRET_KEY.toCharArray()), REGION, SERVICE);
        if (sessionToken != null) {
            config.setSessionToken(new GuardedString(sessionToken.toCharArray()));
        }
        var connector = new AwsTestConnector(script);
        connector.init(config);
        return connector;
    }

    static class AwsTestConfig extends BaseTestConfiguration
            implements RestClientConfiguration.AwsSignatureAuthorization {

        private final String accessKey;
        private final GuardedString secretKey;
        private final String region;
        private final String service;
        private GuardedString sessionToken;

        AwsTestConfig(int port, String testEndpoint, String accessKey, GuardedString secretKey,
                      String region, String service) {
            super(port);
            setRestTestEndpoint(testEndpoint);
            this.accessKey = accessKey;
            this.secretKey = secretKey;
            this.region = region;
            this.service = service;
        }

        @Override public String getRestAwsAccessKey()         { return accessKey; }
        @Override public GuardedString getRestAwsSecretKey()  { return secretKey; }
        @Override public String getRestAwsRegion()            { return region; }
        @Override public String getRestAwsService()           { return service; }
        @Override public GuardedString getRestAwsSessionToken() { return sessionToken; }

        void setSessionToken(GuardedString t)    { this.sessionToken = t; }
    }

    static class AwsTestConnector extends AbstractGroovyRestConnector<BaseRestGroovyConnectorConfiguration> {
        private final String script;

        AwsTestConnector(String script) {
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
