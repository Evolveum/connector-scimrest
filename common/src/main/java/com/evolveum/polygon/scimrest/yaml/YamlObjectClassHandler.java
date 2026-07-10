/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.yaml;

import com.evolveum.polygon.scimrest.groovy.BaseOperationSupportBuilder;
import com.evolveum.polygon.scimrest.groovy.RestHandlerBuilder;
import com.evolveum.polygon.scimrest.yaml.model.YamlOperationDocument;

/**
 * Resolves the object class from a {@link YamlOperationDocument} and routes its operation section to
 * the section handler ({@code search}, {@code create} or {@code update}).
 */
final class YamlObjectClassHandler {

    private final RestHandlerBuilder builder;
    private final YamlSearchHandler searchHandler;
    private final YamlCreateHandler createHandler = new YamlCreateHandler();
    private final YamlUpdateHandler updateHandler = new YamlUpdateHandler();

    YamlObjectClassHandler(RestHandlerBuilder builder, GroovyScriptCompiler scriptCompiler) {
        this.builder = builder;
        this.searchHandler = new YamlSearchHandler(scriptCompiler);
    }

    void load(YamlOperationDocument document) {
        if (document.objectClass == null) {
            return;
        }
        BaseOperationSupportBuilder objectClass = builder.objectClass(document.objectClass);
        searchHandler.load(objectClass, document.search);
        createHandler.load(objectClass, document.create);
        updateHandler.load(objectClass, document.update);
    }
}
