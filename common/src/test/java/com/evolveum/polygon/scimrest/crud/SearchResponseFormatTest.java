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
 * Verifies the {@code responseFormat} search directive: {@code JSON_ARRAY} parses the body
 * as a top-level JSON array, while the default ({@code JSON_OBJECT}) expects a JSON object.
 */
public class SearchResponseFormatTest extends AbstractCrudConnectorTest {

    @Test
    public void jsonArrayResponseFormatParsesTopLevelArray() {
        var script = """
                objectClass("Account") {
                    search {
                        endpoint("accounts") {
                            responseFormat JSON_ARRAY
                            emptyFilterSupported true
                        }
                    }
                }
                """;
        wireMockServer.stubFor(get(urlEqualTo(ACCOUNTS_PATH))
                .willReturn(okJson("[{\"id\":\"1\",\"name\":\"first\"},{\"id\":\"2\",\"name\":\"second\"}]")));

        var results = search(initConnector(script), null);

        assertEquals(results.size(), 2);
    }

    @Test
    public void defaultResponseFormatParsesSingleObject() {
        var script = """
                objectClass("Account") {
                    search {
                        endpoint("accounts") {
                            emptyFilterSupported true
                        }
                    }
                }
                """;
        wireMockServer.stubFor(get(urlEqualTo(ACCOUNTS_PATH))
                .willReturn(okJson("{\"id\":\"1\",\"name\":\"only\"}")));

        var results = search(initConnector(script), null);

        assertEquals(results.size(), 1);
        assertEquals(results.get(0).getUid().getUidValue(), "1");
    }
}
