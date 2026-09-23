/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.crud;

import com.evolveum.polygon.conndev.spi.ClassHandlerConnectorBase;
import com.evolveum.polygon.scimrest.support.AbstractCrudConnectorTest;
import com.evolveum.polygon.scimrest.support.YamlOperationsConnector;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.assertEquals;

/**
 * Verifies that search endpoints may use POST with a JSON body: the {@code method} directive /
 * YAML key selects the HTTP method, {@code request { bodyParameter(…) }} adds static body fields,
 * and the request converter serializes them as JSON with a default content type.
 */
public class PostSearchEndpointTest extends AbstractCrudConnectorTest {

    private static final String GROOVY_SCRIPT = """
            objectClass("Account") {
                search {
                    endpoint("accounts/search") {
                        httpOperation POST
                        responseFormat JSON_OBJECT
                        emptyFilterSupported true
                        objectExtractor {
                            return response.body().get("accounts")
                        }
                        request {
                            bodyParameter("q", "all")
                            bodyParameter("limit", 10)
                        }
                    }
                }
            }
            """;

    private static final String YAML_DOC = """
            objectClasses:
              Account:
                search:
                  endpoints:
                    - path: accounts/search
                      method: POST
                      responseFormat: JSON_OBJECT
                      emptyFilterSupported: true
                      objectExtractor: |
                        response.body().get("accounts")
                      supportedFilters:
                        - spec: |
                            attribute("name").eq().anySingleValue()
                          request: |
                            request.bodyParameter("q", value)
            """;

    @Test
    public void postSearchSendsBodyParameters() {
        wireMockServer.stubFor(post(urlEqualTo("/accounts/search"))
                .willReturn(okJson("""
                        {"accounts": [{"id": "1", "name": "first"}]}
                        """)));

        var results = search(initConnector(GROOVY_SCRIPT), null);

        assertEquals(results.size(), 1);
        assertEquals(results.getFirst().getUid().getUidValue(), "1");
        var requests = wireMockServer.findAll(postRequestedFor(urlEqualTo("/accounts/search")));
        assertEquals(requests.size(), 1);
        var request = requests.getFirst();
        assertEquals(request.getHeader("Content-Type"), "application/json");
        assertEquals(new String(request.getBody(), StandardCharsets.UTF_8), "{\"q\":\"all\",\"limit\":10}");
    }

    @Test
    public void postSearchFromYamlUsesBodyFilterMapping() {
        wireMockServer.stubFor(post(urlEqualTo("/accounts/search"))
                .willReturn(okJson("""
                        {"accounts": [{"id": "1", "name": "first"}, {"id": "2", "name": "second"}]}
                        """)));

        var connector = initConnectorFromYaml();

        var results = search(connector, null);

        assertEquals(results.size(), 2);
        wireMockServer.verify(postRequestedFor(urlEqualTo("/accounts/search")));
    }

    @Test
    public void postSearchFromYamlMapsFilterIntoBody() {
        wireMockServer.stubFor(post(urlEqualTo("/accounts/search"))
                .willReturn(okJson("""
                        {"accounts": [{"id": "1", "name": "first"}]}
                        """)));

        var connector = initConnectorFromYaml();

        var results = search(connector, FilterBuilder.equalTo(AttributeBuilder.build(Name.NAME, "first")));

        assertEquals(results.size(), 1);
        wireMockServer.verify(postRequestedFor(urlEqualTo("/accounts/search"))
                .withRequestBody(equalToJson("{\"q\":\"first\"}")));
    }

    private ClassHandlerConnectorBase initConnectorFromYaml() {
        var connector = YamlOperationsConnector.fromStrings(YAML_DOC);
        connector.init(new SimpleConfig(wireMockServer.port()));
        return connector;
    }
}
