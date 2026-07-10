/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.crud;

import com.evolveum.polygon.scimrest.ClassHandlerConnectorBase;
import com.evolveum.polygon.scimrest.support.AbstractCrudConnectorTest;
import com.evolveum.polygon.scimrest.support.YamlOperationsConnector;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.assertEquals;

/**
 * The remaining search DSL directives in their YAML form: {@code responseFormat} + {@code pagingSupport},
 * {@code normalize}, {@code attributeResolvers} and the fully scripted {@code custom} search. Each case
 * mirrors an existing Groovy test.
 */
public class YamlSearchFullSpecTest extends AbstractCrudConnectorTest {

    private static final String SCHEMA_WITH_ROLES = """
            objectClass("Account") {
                attribute("id") { jsonType "string" }
                attribute("name") { jsonType "string" }
                attribute("roles") {
                    jsonType "string"
                    multiValued true
                }
            }
            """;

    private static final String SCHEMA_WITH_DESCRIPTION = """
            objectClass("Account") {
                attribute("id") { jsonType "string" }
                attribute("name") { jsonType "string" }
                attribute("description") { jsonType "string" }
            }
            """;

    // ----------------------------------------------------------------------------------------------
    // responseFormat + pagingSupport  (mirrors SearchPagingSupportTest)
    // ----------------------------------------------------------------------------------------------

    private static final String PAGING_YAML = """
            objectClass: Account
            search:
              endpoints:
                - path: accounts
                  responseFormat: JSON_ARRAY
                  emptyFilterSupported: true
                  pagingSupport: |
                    request.queryParameter("pageSize", paging.pageSize)
                           .queryParameter("page", paging.pageOffset)
            """;

    @Test
    public void responseFormatAndPagingFromYaml() {
        wireMockServer.stubFor(get(urlPathEqualTo(ACCOUNTS_PATH))
                .willReturn(okJson("""
                        [{"id":"1","name":"first"},{"id":"2","name":"second"}]
                        """)));

        var results = search(initYaml(PAGING_YAML), null);

        assertEquals(results.size(), 2);
        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(ACCOUNTS_PATH))
                .withQueryParam("pageSize", equalTo("25"))
                .withQueryParam("page", equalTo("1"))).size(), 1);
    }

    // ----------------------------------------------------------------------------------------------
    // normalize  (mirrors SearchNormalizationTest)
    // ----------------------------------------------------------------------------------------------

    private static final String NORMALIZE_YAML = """
            objectClass: Account
            search:
              normalize:
                toSingleValue: roles
                rewriteUid: |
                  return original + ":" + value
                rewriteName: |
                  return original + ":" + value
                restoreUid: |
                  return original.substring(0, original.indexOf(':'))
                restoreName: |
                  return original.substring(0, original.indexOf(':'))
              endpoints:
                - path: accounts
                  responseFormat: JSON_ARRAY
                  emptyFilterSupported: true
                - path: accounts/{id}
                  singleResult: true
                  supportedFilters:
                    - spec: |
                        attribute("id").eq().anySingleValue()
                      request: |
                        request.pathParameter("id", value)
            """;

    @Test
    public void normalizationFromYaml() {
        wireMockServer.stubFor(get(urlEqualTo(ACCOUNTS_PATH))
                .willReturn(okJson("[{\"id\":\"1\",\"name\":\"acc\",\"roles\":[\"admin\",\"user\"]}]")));

        var results = search(initYaml(SCHEMA_WITH_ROLES, NORMALIZE_YAML), null);

        assertEquals(results.size(), 2);
        var uids = results.stream().map(o -> o.getUid().getUidValue()).collect(Collectors.toSet());
        assertEquals(uids, Set.of("1:admin", "1:user"));
        var names = results.stream().map(o -> o.getName().getNameValue()).collect(Collectors.toSet());
        assertEquals(names, Set.of("acc:admin", "acc:user"));
        for (var obj : results) {
            assertEquals(obj.getAttributeByName("roles").getValue().size(), 1);
        }
    }

    // ----------------------------------------------------------------------------------------------
    // attributeResolver  (mirrors AttributeResolverTest)
    // ----------------------------------------------------------------------------------------------

    private static final String RESOLVER_YAML = """
            objectClass: Account
            search:
              attributeResolvers:
                - attribute: description
                  implementation: |
                    value.addAttribute("description", "resolved")
              endpoints:
                - path: accounts
                  responseFormat: JSON_ARRAY
                  emptyFilterSupported: true
            """;

    @Test
    public void attributeResolverFromYaml() {
        wireMockServer.stubFor(get(urlEqualTo(ACCOUNTS_PATH))
                .willReturn(okJson("[{\"id\":\"1\",\"name\":\"first\"},{\"id\":\"2\",\"name\":\"second\"}]")));

        var results = search(initYaml(SCHEMA_WITH_DESCRIPTION, RESOLVER_YAML), null);

        assertEquals(results.size(), 2);
        for (var obj : results) {
            assertEquals(obj.getAttributeByName("description").getValue(), List.of("resolved"));
        }
    }

    // ----------------------------------------------------------------------------------------------
    // custom (fully scripted search)  (mirrors CustomSearchTest)
    // ----------------------------------------------------------------------------------------------

    private static final String CUSTOM_YAML = """
            objectClass: Account
            search:
              custom:
                supportedFilters:
                  - spec: |
                      attribute("name").eq().anySingleValue()
                implementation: |
                  var builder = definition().newObjectBuilder()
                  builder.setUid("custom-1")
                  builder.setName("custom-name")
                  resultHandler().handle(builder.build())
              endpoints:
                - path: accounts
                  responseFormat: JSON_ARRAY
                  emptyFilterSupported: true
            """;

    @Test
    public void customSearchFromYaml() {
        var filter = FilterBuilder.equalTo(AttributeBuilder.build(Name.NAME, "custom-name"));

        var results = search(initYaml(CUSTOM_YAML), filter);

        assertEquals(results.size(), 1);
        assertEquals(results.get(0).getUid().getUidValue(), "custom-1");
        // The custom implementation must not touch the REST endpoint at all.
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 0);
    }

    // ----------------------------------------------------------------------------------------------

    private ClassHandlerConnectorBase initYaml(String yaml) {
        var connector = YamlOperationsConnector.fromStrings(yaml);
        connector.init(new SimpleConfig(wireMockServer.port()));
        return connector;
    }

    private ClassHandlerConnectorBase initYaml(String nativeSchema, String yaml) {
        var connector = YamlOperationsConnector.fromStrings(yaml)
                .withSchema(nativeSchema, CONNID_SCHEMA_SCRIPT);
        connector.init(new SimpleConfig(wireMockServer.port()));
        return connector;
    }
}
