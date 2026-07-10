/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.yaml;

import com.evolveum.polygon.scimrest.groovy.BaseOperationSupportBuilder;
import com.evolveum.polygon.scimrest.groovy.api.RestSearchOperationBuilder;
import com.evolveum.polygon.scimrest.yaml.model.YamlSearch;

/**
 * Dispatches the typed {@link YamlSearch} model: resolves the search builder and routes each concern to
 * its own handler (operation-level {@code normalize}/{@code attributeResolvers}/{@code custom} and the
 * {@code endpoints} list). Each sub-handler ignores a {@code null} section.
 */
final class YamlSearchHandler {

    private final YamlNormalizeHandler normalizeHandler;
    private final YamlAttributeResolverHandler resolverHandler;
    private final YamlCustomSearchHandler customHandler;
    private final YamlSearchEndpointHandler endpointHandler;

    YamlSearchHandler(GroovyScriptCompiler scriptCompiler) {
        this.normalizeHandler = new YamlNormalizeHandler(scriptCompiler);
        this.resolverHandler = new YamlAttributeResolverHandler(scriptCompiler);
        this.customHandler = new YamlCustomSearchHandler(scriptCompiler);
        this.endpointHandler = new YamlSearchEndpointHandler(scriptCompiler);
    }

    void load(BaseOperationSupportBuilder objectClass, YamlSearch search) {
        if (search == null) {
            return;
        }
        RestSearchOperationBuilder searchBuilder = objectClass.search();
        normalizeHandler.load(searchBuilder, search.normalize);
        resolverHandler.load(searchBuilder, search.attributeResolvers);
        customHandler.load(searchBuilder, search.custom);
        endpointHandler.load(searchBuilder, search.endpoints);
    }
}
