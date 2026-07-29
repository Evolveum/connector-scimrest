/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.yaml;

import com.evolveum.polygon.conndev.build.api.AttributeResolverBuilder;
import com.evolveum.polygon.conndev.yaml.model.YamlAttributeResolver;
import com.evolveum.polygon.scimrest.groovy.api.RestSearchOperationBuilder;

import java.util.List;

/** Maps the {@code attributeResolvers} list onto {@link AttributeResolverBuilder}s. */
final class YamlAttributeResolverHandler {

    private final GroovyScriptCompiler scriptCompiler;

    YamlAttributeResolverHandler(GroovyScriptCompiler scriptCompiler) {
        this.scriptCompiler = scriptCompiler;
    }

    void load(RestSearchOperationBuilder searchBuilder, List<YamlAttributeResolver> resolvers) {
        if (resolvers == null) {
            return;
        }
        for (YamlAttributeResolver resolver : resolvers) {
            AttributeResolverBuilder rb = searchBuilder.attributeResolver();
            if (resolver.attribute != null) {
                rb.attribute(resolver.attribute);
            }
            if (resolver.resolutionType != null) {
                rb.resolutionType(AttributeResolverBuilder.ResolutionType.valueOf(resolver.resolutionType));
            }
            if (resolver.implementation != null) {
                rb.implementation(scriptCompiler.compile(resolver.implementation));
            }
            // TODO: search-based resolver (rb.search(...)).
        }
    }
}
