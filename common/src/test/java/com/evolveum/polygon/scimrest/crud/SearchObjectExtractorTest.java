/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.crud;

import com.evolveum.polygon.scimrest.support.AbstractCrudConnectorTest;
import org.testng.annotations.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.assertEquals;

/**
 * Verifies the {@code objectExtractor} search directive: the closure receives the HTTP
 * response and extracts the iterable of remote objects from an envelope structure.
 */
public class SearchObjectExtractorTest extends AbstractCrudConnectorTest {

    private static final String SCRIPT = """
            objectClass("Account") {
                search {
                    endpoint("accounts") {
                        emptyFilterSupported true
                        objectExtractor {
                            return response.body().get("_embedded").get("elements")
                        }
                    }
                }
            }
            """;

    @Test
    public void objectsAreExtractedFromEnvelope() {
        wireMockServer.stubFor(get(urlEqualTo(ACCOUNTS_PATH))
                .willReturn(okJson("""
                        {
                          "_embedded": {
                            "elements": [
                              {"id": "1", "name": "first"},
                              {"id": "2", "name": "second"}
                            ]
                          },
                          "total": 2
                        }
                        """)));

        var results = search(initConnector(SCRIPT), null);

        assertEquals(results.size(), 2);
        var uids = results.stream().map(o -> o.getUid().getUidValue()).collect(Collectors.toSet());
        assertEquals(uids, Set.of("1", "2"));
        var names = results.stream().map(o -> o.getName().getNameValue()).collect(Collectors.toSet());
        assertEquals(names, Set.of("first", "second"));
    }
}
