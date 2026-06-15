/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.crud;

import com.evolveum.polygon.scimrest.support.AbstractCrudConnectorTest;
import org.testng.annotations.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.assertEquals;

/**
 * Verifies the {@code attributeResolver { }} search directive: a per-object resolver
 * whose {@code implementation} closure fills in attributes missing from the search response.
 */
public class AttributeResolverTest extends AbstractCrudConnectorTest {

    private static final String SCHEMA_WITH_DESCRIPTION = """
            objectClass("Account") {
                attribute("id") { jsonType "string" }
                attribute("name") { jsonType "string" }
                attribute("description") { jsonType "string" }
            }
            """;

    private static final String SCRIPT = """
            objectClass("Account") {
                search {
                    endpoint("accounts") {
                        responseFormat JSON_ARRAY
                        emptyFilterSupported true
                    }
                    attributeResolver {
                        attribute "description"
                        implementation {
                            value.addAttribute("description", "resolved")
                        }
                    }
                }
            }
            """;

    @Test
    public void missingAttributeIsFilledInByResolver() {
        wireMockServer.stubFor(get(urlEqualTo(ACCOUNTS_PATH))
                .willReturn(okJson("[{\"id\":\"1\",\"name\":\"first\"},{\"id\":\"2\",\"name\":\"second\"}]")));

        var results = search(initConnector(SCHEMA_WITH_DESCRIPTION, CONNID_SCHEMA_SCRIPT, SCRIPT), null);

        assertEquals(results.size(), 2);
        for (var obj : results) {
            assertEquals(obj.getAttributeByName("description").getValue(), java.util.List.of("resolved"));
        }
    }
}
