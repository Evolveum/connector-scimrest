/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */

package com.evolveum.polygon.openProject;

import com.evolveum.polygon.conndev.groovy.GroovySchemaLoader;
import com.evolveum.polygon.conndev.groovy.GroovyScriptLoader;
import com.evolveum.polygon.scimrest.groovy.connector.AbstractGroovyRestConnector;
import com.evolveum.polygon.scimrest.groovy.handler.GroovyRestHandlerBuilder;
import org.testng.annotations.Test;

/**
 * Loads the YAML operation documents of this bundle through the YAML front-end and builds the
 * handlers — no network access. Guards the built-in spellings the codegen microservice emits
 * ({@code contentType: APPLICATION_JSON}, {@code body: EMPTY}), which must behave exactly like
 * their Groovy-DSL counterparts (bugs 12410 / 12411).
 */
public class YamlSampleDocumentsTest {

    @Test
    public void userYamlOperationDocumentsBuild() {
        var connector = new UserOnlyConnector();
        connector.init(new OpenProjectConfiguration());
        connector.schema();

        var builder = connector.context().handlerBuilder(connector.context().configuration().groovyContext());
        builder.loadFromResource("/User.op.yaml");
        builder.loadFromResource("/User.update.op.yaml");
        builder.build();
    }

    /** Schema is limited to User — the object class the tested operation documents reference. */
    static class UserOnlyConnector extends AbstractGroovyRestConnector {

        @Override
        protected void initializeSchema(GroovySchemaLoader loader) {
            loader.loadFromResource("/User.native.schema.groovy");
            loader.loadFromResource("/User.connid.schema.groovy");
        }

        @Override
        protected void initializeAuthorizationHandler(GroovyRestHandlerBuilder builder) {
        }

        @Override
        protected void initializeObjectClassHandler(GroovyScriptLoader builder) {
        }
    }
}
