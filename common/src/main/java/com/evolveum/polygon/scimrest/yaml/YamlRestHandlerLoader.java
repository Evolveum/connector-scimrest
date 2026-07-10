/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.yaml;

import com.evolveum.polygon.scimrest.groovy.GroovyContext;
import com.evolveum.polygon.scimrest.groovy.RestHandlerBuilder;
import com.evolveum.polygon.scimrest.yaml.model.YamlOperationDocument;

import java.io.Reader;
import java.util.List;

/**
 * YAML front-end of connector operation/authentication scripts — the YAML counterpart of
 * {@link com.evolveum.polygon.scimrest.groovy.GroovyRestHandlerBuilder}. Each document (one operation
 * per file, mirroring the wizard convention) is parsed and validated by {@link YamlOperationLoader}
 * and immediately bound onto the same format-agnostic {@link RestHandlerBuilder} the Groovy DSL
 * drives, so operations defined in YAML execute exactly like their Groovy counterparts and both
 * front-ends can contribute to one connector.
 */
public class YamlRestHandlerLoader {

    private final YamlOperationLoader operationLoader = new YamlOperationLoader();
    private final YamlObjectClassHandler objectClassHandler;
    private final YamlAuthenticationHandler authenticationHandler;

    public YamlRestHandlerLoader(RestHandlerBuilder builder, GroovyContext groovyContext) {
        var scriptCompiler = new GroovyScriptCompiler(groovyContext);
        this.objectClassHandler = new YamlObjectClassHandler(builder, scriptCompiler);
        this.authenticationHandler = new YamlAuthenticationHandler(builder, scriptCompiler);
    }

    public void loadFromString(String yaml) {
        operationLoader.load(yaml);
        bind(lastDocument());
    }

    public void load(Reader reader, String sourceName) {
        operationLoader.load(reader, sourceName);
        bind(lastDocument());
    }

    private YamlOperationDocument lastDocument() {
        var documents = operationLoader.documents();
        return documents.get(documents.size() - 1);
    }

    private void bind(YamlOperationDocument document) {
        objectClassHandler.load(document);
        authenticationHandler.load(document.authentication);
    }

    /** All documents loaded so far, in loading order. */
    public List<YamlOperationDocument> documents() {
        return operationLoader.documents();
    }
}
