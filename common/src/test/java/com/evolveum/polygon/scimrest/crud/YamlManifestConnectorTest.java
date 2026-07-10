/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.crud;

import com.evolveum.polygon.scimrest.groovy.impl.ManifestBasedConnector;
import com.evolveum.polygon.scimrest.support.AbstractCrudConnectorTest;
import org.testng.annotations.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.assertEquals;

/**
 * A fully manifest-driven connector whose manifest is written in <b>YAML</b>: the manifest
 * ({@code /manifests/e2e/connector.manifest.yaml}) references Groovy schema scripts and a YAML
 * operation document, and the search operation executes end to end.
 */
public class YamlManifestConnectorTest extends AbstractCrudConnectorTest {

    /** Only the manifest location differs from a production connector. */
    private static class YamlManifestConnector extends ManifestBasedConnector {
        YamlManifestConnector() {
            super("/manifests/e2e/connector.manifest");
        }
    }

    @Test
    public void yamlManifestDrivesTheConnector() {
        wireMockServer.stubFor(get(urlEqualTo(ACCOUNTS_PATH))
                .willReturn(okJson("""
                        {"_embedded": {"elements": [{"id": "1", "name": "first"}]}}
                        """)));

        var connector = new YamlManifestConnector();
        connector.init(new SimpleConfig(wireMockServer.port()));

        var results = search(connector, null);

        assertEquals(results.size(), 1);
        assertEquals(results.get(0).getUid().getUidValue(), "1");
        assertEquals(results.get(0).getName().getNameValue(), "first");
    }
}
