package com.evolveum.polygon.scim.rest.groovy;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public interface ScriptedGroovySearchBuilder {


    ScriptedGroovySearchBuilder emptyFilterSupported(boolean emptyFilterSupported);

    ScriptedGroovySearchBuilder implementation(@DelegatesTo(SearchScriptContext.class) Closure<?> implementation);
}
