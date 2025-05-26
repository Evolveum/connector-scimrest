package com.evolveum.polygon.scim.rest.groovy.api;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public interface SearchScriptBuilder {


    SearchScriptBuilder emptyFilterSupported(boolean emptyFilterSupported);

    SearchScriptBuilder implementation(@DelegatesTo(SearchScriptContext.class) Closure<?> implementation);
}
