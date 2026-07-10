/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.unit.groovy;

import com.evolveum.polygon.scimrest.groovy.impl.ConnectorManifest;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.expectThrows;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

/**
 * Resolution and parsing of the connector manifest: the manifest may be written in YAML
 * ({@code connector.manifest.yaml}/{@code .yml}) or JSON ({@code connector.manifest.json}) with the
 * same structure; exactly one format may be bundled; a missing manifest keeps yielding the empty one.
 */
public class ConnectorManifestTest {

    @Test
    public void yamlManifestIsLoaded() {
        var manifest = ConnectorManifest.load(getClass(), "/manifests/yaml/connector.manifest");

        assertEquals(List.of("/User.native.schema.groovy", "/User.connid.schema.groovy"), manifest.schemaScripts());
        assertEquals(List.of("/authorization.op.yaml"), manifest.authorizationScripts());
        assertEquals(List.of("/User.search.all.op.yaml"), manifest.operationScripts());
    }

    @Test
    public void jsonManifestKeepsLoading() {
        var manifest = ConnectorManifest.load(getClass(), "/manifests/json/connector.manifest");

        assertEquals(List.of("/User.native.schema.groovy", "/User.connid.schema.groovy"), manifest.schemaScripts());
        assertEquals(List.of("/authorization.op.yaml"), manifest.authorizationScripts());
        assertEquals(List.of("/User.search.all.op.yaml"), manifest.operationScripts());
    }

    /**
     * A missing manifest loads as the empty one (test connectors override the initialize methods and
     * never touch it); reading scripts from it keeps failing like before.
     */
    @Test
    public void missingManifestYieldsEmptyManifest() {
        var manifest = ConnectorManifest.load(getClass(), "/manifests/missing/connector.manifest");

        var exception = expectThrows(IllegalArgumentException.class, manifest::schemaScripts);
        assertTrue(exception.getMessage().contains("connector object not present"));
    }

    /** Two manifest formats in one bundle are two sources of truth — a packaging error. */
    @Test
    public void bundlingBothFormatsIsRejected() {
        var exception = expectThrows(ConfigurationException.class,
                () -> ConnectorManifest.load(getClass(), "/manifests/both/connector.manifest"));
        assertTrue(exception.getMessage().contains("connector.manifest.yaml"));
        assertTrue(exception.getMessage().contains("connector.manifest.json"));
    }
}
