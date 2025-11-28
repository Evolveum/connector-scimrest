/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy.api;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public interface RestSearchOperationBuilder {

    RestSearchEndpointBuilder endpoint(String path);

    RestSearchEndpointBuilder endpoint(String path,
                                       @DelegatesTo(value = RestSearchEndpointBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> builder);

    AttributeResolverBuilder attributeResolver(
            @DelegatesTo(value = AttributeResolverBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> definition);

    NormalizationBuilder normalize(@DelegatesTo(value = NormalizationBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> definition);

    SearchScriptBuilder custom(@DelegatesTo(SearchScriptBuilder.class) Closure<?> definition);

    ScimSearchBuilder scim();

}
