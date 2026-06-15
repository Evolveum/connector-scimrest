/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.crud;

import com.evolveum.polygon.scimrest.support.AbstractCrudConnectorTest;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.testng.annotations.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.assertEquals;

/**
 * Verifies the {@code normalize { }} search directive: a multi-valued attribute is split
 * into one object per value ({@code toSingleValue}), with UID/Name rewritten by the
 * {@code rewriteUid}/{@code rewriteName} closures, and incoming UID filters mapped back
 * by {@code restoreUid}/{@code restoreName}.
 */
public class SearchNormalizationTest extends AbstractCrudConnectorTest {

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

    private static final String SCRIPT = """
            objectClass("Account") {
                search {
                    normalize {
                        toSingleValue "roles"
                        rewriteUid { return original + ":" + value }
                        rewriteName { return original + ":" + value }
                        restoreUid { return original.substring(0, original.indexOf(':')) }
                        restoreName { return original.substring(0, original.indexOf(':')) }
                    }
                    endpoint("accounts") {
                        responseFormat JSON_ARRAY
                        emptyFilterSupported true
                    }
                    endpoint("accounts/{id}") {
                        singleResult()
                        supportedFilter(attribute("id").eq().anySingleValue()) {
                            request.pathParameter("id", value)
                        }
                    }
                }
            }
            """;

    @Test
    public void multiValuedAttributeIsSplitIntoOneObjectPerValue() {
        wireMockServer.stubFor(get(urlEqualTo(ACCOUNTS_PATH))
                .willReturn(okJson("[{\"id\":\"1\",\"name\":\"acc\",\"roles\":[\"admin\",\"user\"]}]")));

        var results = search(initConnector(SCHEMA_WITH_ROLES, CONNID_SCHEMA_SCRIPT, SCRIPT), null);

        assertEquals(results.size(), 2);
        var uids = results.stream().map(o -> o.getUid().getUidValue()).collect(Collectors.toSet());
        assertEquals(uids, Set.of("1:admin", "1:user"));
        var names = results.stream().map(o -> o.getName().getNameValue()).collect(Collectors.toSet());
        assertEquals(names, Set.of("acc:admin", "acc:user"));
        for (var obj : results) {
            assertEquals(obj.getAttributeByName("roles").getValue().size(), 1);
        }
    }

    @Test
    public void uidFilterIsRestoredToOriginalUidForTheRequest() {
        wireMockServer.stubFor(get(urlEqualTo(ACCOUNT_BY_ID_PATH.replace("123", "1")))
                .willReturn(okJson("{\"id\":\"1\",\"name\":\"acc\",\"roles\":[\"admin\",\"user\"]}")));

        var filter = FilterBuilder.equalTo(new Uid("1:admin"));
        var results = search(initConnector(SCHEMA_WITH_ROLES, CONNID_SCHEMA_SCRIPT, SCRIPT), filter);

        // The remote system was queried with the restored uid "1"...
        assertEquals(wireMockServer.findAll(getRequestedFor(urlEqualTo("/accounts/1"))).size(), 1);
        // ...and only the normalized object matching the queried uid is returned
        assertEquals(results.size(), 1);
        assertEquals(results.get(0).getUid().getUidValue(), "1:admin");
    }
}
