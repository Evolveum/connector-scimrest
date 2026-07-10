/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.yaml;

import com.evolveum.polygon.scimrest.groovy.api.FilterSpecification;
import com.evolveum.polygon.scimrest.groovy.api.RestSearchEndpointBuilder;
import com.evolveum.polygon.scimrest.groovy.api.RestSearchOperationBuilder;
import com.evolveum.polygon.scimrest.yaml.model.YamlEndpointFilter;
import com.evolveum.polygon.scimrest.yaml.model.YamlSearchEndpoint;

import java.util.List;

/**
 * Maps the typed {@code endpoints} list onto per-endpoint {@link RestSearchEndpointBuilder}s. Declarative
 * fields drive the builder directly; {@code objectExtractor}, {@code pagingSupport} and the
 * {@code supportedFilters} {@code spec}/{@code request} are Groovy blocks bridged via {@link GroovyScriptCompiler}.
 */
final class YamlSearchEndpointHandler {

    private final GroovyScriptCompiler scriptCompiler;

    YamlSearchEndpointHandler(GroovyScriptCompiler scriptCompiler) {
        this.scriptCompiler = scriptCompiler;
    }

    void load(RestSearchOperationBuilder searchBuilder, List<YamlSearchEndpoint> endpoints) {
        if (endpoints == null) {
            return;
        }
        for (YamlSearchEndpoint endpoint : endpoints) {
            RestSearchEndpointBuilder ep = searchBuilder.endpoint(endpoint.path);
            if (endpoint.responseFormat != null) {
                ep.responseFormat(responseFormatFor(endpoint.responseFormat));
            }
            if (Boolean.TRUE.equals(endpoint.emptyFilterSupported)) {
                ep.emptyFilterSupported(true);
            }
            if (endpoint.objectExtractor != null) {
                ep.objectExtractor(scriptCompiler.compile(endpoint.objectExtractor));
            }
            if (endpoint.pagingSupport != null) {
                ep.pagingSupport(scriptCompiler.compile(endpoint.pagingSupport));
            }
            if (Boolean.TRUE.equals(endpoint.singleResult)) {
                ep.singleResult();
            }
            if (endpoint.supportedFilters != null) {
                loadSupportedFilters(ep, endpoint.supportedFilters);
            }
        }
    }

    /**
     * Each supported filter carries a {@code spec} (evaluated against the endpoint builder to a
     * {@link FilterSpecification}) and a {@code request} block mapping the matched filter onto the request.
     */
    private void loadSupportedFilters(RestSearchEndpointBuilder ep, List<YamlEndpointFilter> filters) {
        for (YamlEndpointFilter filter : filters) {
            FilterSpecification spec = (FilterSpecification) scriptCompiler.evaluate(filter.spec, ep);
            ep.supportedFilter(spec, scriptCompiler.compile(filter.request));
        }
    }

    private Class<?> responseFormatFor(String name) {
        return switch (name) {
            case "JSON_ARRAY" -> RestSearchEndpointBuilder.JSON_ARRAY;
            case "JSON_OBJECT" -> RestSearchEndpointBuilder.JSON_OBJECT;
            default -> throw new IllegalArgumentException("Unsupported responseFormat: " + name);
        };
    }
}
