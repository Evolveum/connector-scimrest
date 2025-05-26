package com.evolveum.polygon.scim.rest.groovy.api;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public interface SearchOperationBuilder {

    SearchEndpointBuilder endpoint(String path);

    SearchEndpointBuilder endpoint(String path, @DelegatesTo(value = SearchEndpointBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> builder);

    AttributeResolverBuilder attributeResolver(@DelegatesTo(value = AttributeResolverBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> definition);

    SearchScriptBuilder custom(@DelegatesTo(SearchScriptBuilder.class) Closure<?> definition);
}
