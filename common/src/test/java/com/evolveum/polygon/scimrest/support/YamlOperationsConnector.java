/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.support;

import com.evolveum.polygon.scimrest.groovy.AbstractGroovyRestConnector;
import com.evolveum.polygon.scimrest.groovy.BaseRestGroovyConnectorConfiguration;
import com.evolveum.polygon.scimrest.groovy.GroovyRestHandlerBuilder;
import com.evolveum.polygon.scimrest.groovy.GroovySchemaLoader;
import com.evolveum.polygon.scimrest.yaml.YamlRestHandlerLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * Test connector with schema from Groovy scripts and operations/authentication from YAML: inline
 * documents go through {@link YamlRestHandlerLoader} (one document per operation, mirroring the
 * wizard file convention), resource paths go through the standard
 * {@code HandlerDefinitionBuilder#loadFromResource} dispatch — including the Groovy → YAML fallback.
 */
public class YamlOperationsConnector extends AbstractGroovyRestConnector<BaseRestGroovyConnectorConfiguration> {

    private String nativeSchemaScript = AbstractCrudConnectorTest.NATIVE_SCHEMA_SCRIPT;
    private String connIdSchemaScript = AbstractCrudConnectorTest.CONNID_SCHEMA_SCRIPT;
    private final List<String> yamlDocuments = new ArrayList<>();
    private final List<String> operationResources = new ArrayList<>();
    private final List<String> groovyOperationScripts = new ArrayList<>();
    private final List<String> yamlAuthDocuments = new ArrayList<>();

    public static YamlOperationsConnector fromStrings(String... yamlDocuments) {
        var connector = new YamlOperationsConnector();
        connector.yamlDocuments.addAll(List.of(yamlDocuments));
        return connector;
    }

    public static YamlOperationsConnector fromResources(String... resources) {
        var connector = new YamlOperationsConnector();
        connector.operationResources.addAll(List.of(resources));
        return connector;
    }

    public YamlOperationsConnector withSchema(String nativeSchemaScript, String connIdSchemaScript) {
        this.nativeSchemaScript = nativeSchemaScript;
        this.connIdSchemaScript = connIdSchemaScript;
        return this;
    }

    public YamlOperationsConnector withGroovyOperations(String... scripts) {
        groovyOperationScripts.addAll(List.of(scripts));
        return this;
    }

    public YamlOperationsConnector withYamlAuthentication(String... yamlDocuments) {
        yamlAuthDocuments.addAll(List.of(yamlDocuments));
        return this;
    }

    @Override
    protected void initializeSchema(GroovySchemaLoader loader) {
        loader.load(nativeSchemaScript);
        loader.load(connIdSchemaScript);
    }

    @Override
    protected void initializeAuthorizationHandler(GroovyRestHandlerBuilder builder) {
        var loader = new YamlRestHandlerLoader(builder, getConfiguration().groovyContext());
        yamlAuthDocuments.forEach(loader::loadFromString);
    }

    @Override
    protected void initializeObjectClassHandler(GroovyRestHandlerBuilder builder) {
        groovyOperationScripts.forEach(builder::loadFromString);
        var loader = new YamlRestHandlerLoader(builder, getConfiguration().groovyContext());
        yamlDocuments.forEach(loader::loadFromString);
        operationResources.forEach(builder::loadFromResource);
    }
}
