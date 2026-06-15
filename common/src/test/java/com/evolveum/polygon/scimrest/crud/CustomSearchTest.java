/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.crud;

import com.evolveum.polygon.scimrest.support.AbstractCrudConnectorTest;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.testng.annotations.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.assertEquals;

/**
 * Verifies the {@code custom { }} scripted search: filters declared via
 * {@code supportedFilter} are dispatched to the Groovy {@code implementation} closure
 * instead of a REST endpoint, while other filters still go to the endpoint.
 */
public class CustomSearchTest extends AbstractCrudConnectorTest {

    private static final String SCRIPT = """
            objectClass("Account") {
                search {
                    endpoint("accounts") {
                        responseFormat JSON_ARRAY
                        emptyFilterSupported true
                    }
                    custom {
                        supportedFilter(attribute("name").eq().anySingleValue())
                        implementation {
                            var builder = definition().newObjectBuilder()
                            builder.setUid("custom-1")
                            builder.setName("custom-name")
                            resultHandler().handle(builder.build())
                        }
                    }
                }
            }
            """;

    @Test
    public void supportedFilterIsHandledByImplementationClosure() {
        var filter = FilterBuilder.equalTo(AttributeBuilder.build(Name.NAME, "custom-name"));
        var results = search(initConnector(SCRIPT), filter);

        assertEquals(results.size(), 1);
        assertEquals(results.get(0).getUid().getUidValue(), "custom-1");
        // The custom implementation must not touch the REST endpoint at all
        assertEquals(wireMockServer.findAll(anyRequestedFor(anyUrl())).size(), 0);
    }

    @Test
    public void emptyFilterStillGoesToEndpoint() {
        wireMockServer.stubFor(get(urlEqualTo(ACCOUNTS_PATH))
                .willReturn(okJson("[{\"id\":\"1\",\"name\":\"from-endpoint\"}]")));

        var results = search(initConnector(SCRIPT), null);

        assertEquals(results.size(), 1);
        assertEquals(results.get(0).getName().getNameValue(), "from-endpoint");
        assertEquals(wireMockServer.findAll(getRequestedFor(urlEqualTo(ACCOUNTS_PATH))).size(), 1);
    }
}
