/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 */
package com.evolveum.polygon.scimrest.crud;

import com.evolveum.polygon.scimrest.groovy.impl.ManifestBasedConnector;
import com.evolveum.polygon.scimrest.support.AbstractCrudConnectorTest;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectReference;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.Uid;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

/**
Verifies the documented membership configuration through a manifest containing only YAML schema and operation resources.
*/
public class YamlRelationshipTest extends AbstractCrudConnectorTest {

    private static final class MembershipConnector extends ManifestBasedConnector {
        private MembershipConnector() {
            super("/relationships/connector.manifest");
        }
    }

    @Test
    public void testYamlManifest() {
        // Given: Memberships configured entirely through YAML
        wireMockServer.stubFor(get(urlEqualTo("/users")).willReturn(okJson("[{\"id\":\"42\",\"name\":\"Alice\"}]")));
        wireMockServer.stubFor(get(urlPathEqualTo("/memberships")).withQueryParam("userId", equalTo("42"))
                .withQueryParam("offset", equalTo("0")).withQueryParam("limit", equalTo("100"))
                .willReturn(okJson("{\"result\":[{\"userId\":\"42\",\"groupId\":\"engineering\"},{\"userId\":\"42\",\"groupId\":\"support\"}]}")));
        wireMockServer.stubFor(get(urlPathEqualTo("/memberships")).withQueryParam("userId", equalTo("42"))
                .withQueryParam("offset", equalTo("2")).withQueryParam("limit", equalTo("100")).willReturn(okJson("{\"result\":[]}")));
        var connector = new MembershipConnector();
        connector.init(new SimpleConfig(wireMockServer.port()));
        var results = new ArrayList<ConnectorObject>();

        // When: Search through the manifest connector
        connector.executeQuery(new ObjectClass("User"), null, result -> results.add(result), new OperationOptionsBuilder().build());

        // Then: Resolve typed references without group lookups
        assertEquals(results.size(), 1);
        assertEquals(results.getFirst().getUid().getUidValue(), "42");
        assertEquals(results.getFirst().getName().getNameValue(), "Alice");
        var values = results.getFirst().getAttributeByName("groups").getValue();
        assertEquals(values.size(), 2);
        var targets = new ArrayList<String>();
        for (var value : values) {
            var reference = (ConnectorObjectReference) value;
            assertEquals(reference.getValue().getObjectClass(), new ObjectClass("Group"));
            assertFalse(reference.hasObject());
            targets.add(AttributeUtil.getStringValue(reference.getValue().getAttributeByName(Uid.NAME)));
        }
        assertEquals(targets, List.of("engineering", "support"));
        assertEquals(wireMockServer.getAllServeEvents().size(), 3);
        wireMockServer.verify(1, getRequestedFor(urlEqualTo("/users")));
        for (var offset : List.of("0", "2")) {
            wireMockServer.verify(1, getRequestedFor(urlPathEqualTo("/memberships")).withQueryParam("userId", equalTo("42"))
                    .withQueryParam("offset", equalTo(offset)).withQueryParam("limit", equalTo("100")));
        }
    }
}
