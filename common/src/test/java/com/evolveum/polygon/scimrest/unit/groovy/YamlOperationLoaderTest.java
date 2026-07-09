/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.unit.groovy;

import com.evolveum.polygon.scimrest.groovy.ConnectorContext;
import com.evolveum.polygon.scimrest.groovy.GroovyContext;
import com.evolveum.polygon.scimrest.groovy.HandlerDefinitionBuilder;
import com.evolveum.polygon.scimrest.yaml.YamlOperationLoader;
import org.testng.annotations.Test;

import static org.testng.Assert.expectThrows;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertNull;
import static org.testng.AssertJUnit.assertTrue;

/**
 * Inert loading of the per-operation YAML documents. The resources mirror how a wizard-generated
 * connector really looks (see {@code ConnectorDevelopmentArtifacts.KnownArtifactType}): search is
 * split into three files by intent — {@code Account.search.all.op.yaml},
 * {@code Account.search.id.op.yaml}, {@code Account.search.filter.op.yaml} — plus
 * {@code Account.create.op.yaml}; each file carries exactly one operation of one object class.
 */
public class YamlOperationLoaderTest {

    @Test
    public void wizardStyleOperationFilesBuildTypedDocuments() {
        var builder = new HandlerDefinitionBuilder(new GroovyContext(), new ConnectorContext(null));
        builder.loadFromResource("/yaml/Account.search.all.op.yaml");
        builder.loadFromResource("/yaml/Account.search.id.op.yaml");
        builder.loadFromResource("/yaml/Account.search.filter.op.yaml");
        builder.loadFromResource("/yaml/Account.create.op.yaml");

        var documents = builder.yamlOperations();
        assertEquals(4, documents.size());

        var searchAll = documents.get(0);
        assertEquals("Account", searchAll.objectClass);
        assertEquals(1, searchAll.search.endpoints.size());
        assertEquals("accounts", searchAll.search.endpoints.get(0).path);
        assertEquals(Boolean.TRUE, searchAll.search.endpoints.get(0).emptyFilterSupported);
        assertNotNull(searchAll.search.endpoints.get(0).objectExtractor);
        assertEquals("roles", searchAll.search.normalize.toSingleValue);
        assertNull(searchAll.create);

        var searchById = documents.get(1);
        assertEquals(Boolean.TRUE, searchById.search.endpoints.get(0).singleResult);
        var idFilter = searchById.search.endpoints.get(0).supportedFilters.get(0);
        assertTrue(idFilter.spec.contains("attribute(\"id\")"));
        assertTrue(idFilter.request.contains("pathParameter"));

        var searchFilter = documents.get(2);
        assertEquals("accounts/search", searchFilter.search.endpoints.get(0).path);

        var create = documents.get(3).create.endpoints.get(0);
        assertEquals("POST", create.method);
        assertEquals("application/json", create.request.contentType);
        assertNotNull(create.request.body);
        assertEquals(2, create.supportedAttributes.size());
        assertEquals("name", create.supportedAttributes.get(0).name);
        assertEquals("status", create.supportedAttributes.get(1).name);
        assertEquals("active", create.supportedAttributes.get(1).value);
    }

    /**
     * A manifest keeps referencing the conventional Groovy file names; when the bundle carries the
     * artifact as YAML instead, the loader falls back to it.
     */
    @Test
    public void missingGroovyScriptFallsBackToYaml() {
        var builder = new HandlerDefinitionBuilder(new GroovyContext(), new ConnectorContext(null));
        builder.loadFromResource("/yaml/Account.search.all.op.groovy");

        assertEquals(1, builder.yamlOperations().size());
        assertEquals("Account", builder.yamlOperations().get(0).objectClass);
    }

    @Test
    public void missingScriptWithoutYamlFallbackIsRejected() {
        var builder = new HandlerDefinitionBuilder(new GroovyContext(), new ConnectorContext(null));
        var exception = expectThrows(IllegalArgumentException.class,
                () -> builder.loadFromResource("/yaml/Nonexistent.search.all.op.groovy"));
        assertTrue(exception.getMessage().contains("YAML fallback"));
    }

    /** The counterpart of {@code authentication.op.groovy} — no object class. */
    @Test
    public void authenticationDocumentNeedsNoObjectClass() {
        var loader = new YamlOperationLoader();
        loader.load("""
                authentication:
                  rest:
                    basic:
                      implementation: |
                        username configuration.username
                    preference:
                      - basic
                """);
        assertEquals(1, loader.documents().size());
        assertNotNull(loader.documents().get(0).authentication.rest.basic.implementation);
    }

    @Test
    public void operationWithoutObjectClassIsRejected() {
        var exception = expectThrows(IllegalArgumentException.class, () -> new YamlOperationLoader().load("""
                search:
                  endpoints:
                    - path: accounts
                """));
        assertTrue(exception.getMessage().contains("no objectClass"));
    }

    @Test
    public void multipleOperationsPerDocumentAreRejected() {
        var exception = expectThrows(IllegalArgumentException.class, () -> new YamlOperationLoader().load("""
                objectClass: Account
                search:
                  endpoints:
                    - path: accounts
                create:
                  endpoints:
                    - method: POST
                      path: accounts
                """));
        assertTrue(exception.getMessage().contains("exactly one operation"));
    }

    @Test
    public void unknownKeysFailFast() {
        expectThrows(IllegalArgumentException.class, () -> new YamlOperationLoader().load("""
                objectClass: Account
                search:
                  endpoints:
                    - path: accounts
                      unknownKey: value
                """));
    }
}
