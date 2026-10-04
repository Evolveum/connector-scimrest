/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */

package com.evolveum.polygon.openProject;

import org.identityconnectors.framework.common.objects.ObjectClass;
import org.testng.annotations.Test;

import static org.testng.Assert.assertNotNull;

/**
 * Builds the search documents of this bundle through both fronts (Groovy and YAML) — no network
 * access. Guards the {@code notFoundIsNoResult} declaration the {@code {id}} search endpoints
 * carry (bug #12507: a 404 for a nonexistent object must be an empty result set, not a
 * ConfigurationException).
 */
public class SearchDocumentsBuildTest {

    private static final String[] OBJECT_CLASSES = {"User", "Group", "Project", "Role", "Membership"};

    private static final String[] SEARCH_YAML = {
            "/User.search.yaml",
            "/Group.search.yaml",
            "/Project.search.yaml",
            "/Role.search.yaml",
            "/Membership.search.yaml",
    };

    /** The Groovy documents are the ones the connector loads at runtime. */
    @Test
    public void groovySearchDocumentsBuild() {
        var connector = new OpenProjectConnector();
        connector.init(new OpenProjectConfiguration());

        for (var objectClass : OBJECT_CLASSES) {
            assertNotNull(connector.handlerFor(new ObjectClass(objectClass)));
        }
    }

    /** The YAML documents are the codegen microservice's output and must bind the same way. */
    @Test
    public void yamlSearchDocumentsBuild() {
        var connector = new OpenProjectConnector();
        connector.init(new OpenProjectConfiguration());
        connector.schema();

        var builder = connector.context().handlerBuilder(connector.context().configuration().groovyContext());
        for (var resource : SEARCH_YAML) {
            builder.loadFromResource(resource);
        }
        builder.build();
    }
}
