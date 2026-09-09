/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy.api;

import com.evolveum.polygon.conndev.build.api.AttributeResolverBuilder;
import com.evolveum.polygon.conndev.build.api.NormalizationBuilder;
import com.evolveum.polygon.conndev.build.api.SearchOperationBuilder;
import com.evolveum.polygon.conndev.build.api.SearchScriptBuilder;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import com.evolveum.polygon.conndev.annotations.Script;
import com.evolveum.polygon.conndev.annotations.Yaml;
import com.evolveum.polygon.scimrest.groovy.api.scim.ScimSearchBuilder;
import com.evolveum.polygon.scimrest.yaml.binding.AttributeResolversHandler;
import com.evolveum.polygon.scimrest.yaml.binding.CustomSearchHandler;
import com.evolveum.polygon.scimrest.yaml.binding.NormalizationHandler;
import com.evolveum.polygon.scimrest.yaml.binding.SearchEndpointsHandler;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public interface RestSearchOperationBuilder extends SearchOperationBuilder {

    RestSearchEndpointBuilder endpoint(String path);

    /**
     * Marker for the YAML front-end: the {@code endpoints:} block is bound by
     * {@link SearchEndpointsHandler}, which calls {@link #endpoint(String)} per list item. The method
     * body is unused.
     */
    @Yaml.Custom(SearchEndpointsHandler.class)
    default void endpoints() {
    }

    RestSearchEndpointBuilder endpoint(String path,
                                       @DelegatesTo(value = RestSearchEndpointBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> builder);

    AttributeResolverBuilder attributeResolver();

    default AttributeResolverBuilder attributeResolver(
            @DelegatesTo(value = AttributeResolverBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> definition) {
        return GroovyClosures.callAndReturnDelegate(definition, attributeResolver());
    }

    /**
     * Marker for the YAML front-end: the {@code normalize:} block is bound by
     * {@link NormalizationHandler} onto the {@link NormalizationBuilder} this returns. The method body
     * is unused.
     */
    @Yaml.Custom(NormalizationHandler.class)
    NormalizationBuilder normalize();

    default NormalizationBuilder normalize(@DelegatesTo(value = NormalizationBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> definition) {
        return GroovyClosures.callAndReturnDelegate(definition, normalize());
    }

    /**
     * Marker for the YAML front-end: the {@code attributeResolvers:} list is bound by
     * {@link AttributeResolversHandler}, which calls {@link #attributeResolver()} per list item. The
     * method body is unused.
     */
    @Yaml.Custom(AttributeResolversHandler.class)
    default void attributeResolvers() {
    }

    /**
     * Marker for the YAML front-end: the {@code custom:} block is bound by
     * {@link CustomSearchHandler} onto the {@link SearchScriptBuilder} this returns. The method body is
     * unused.
     */
    @Yaml.Custom(CustomSearchHandler.class)
    SearchScriptBuilder custom();

    default SearchScriptBuilder custom(@DelegatesTo(SearchScriptBuilder.class) Closure<?> definition) {
        return GroovyClosures.callAndReturnDelegate(definition, custom());
    }

    ScimSearchBuilder scim();

    default ScimSearchBuilder scim(@DelegatesTo(value = ScimSearchBuilder.class, strategy = Closure.DELEGATE_ONLY)
                                   @Script.Initialization
                                   Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, scim());
    }



}
