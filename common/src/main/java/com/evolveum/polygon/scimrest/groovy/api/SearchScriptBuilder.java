/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy.api;

import com.evolveum.polygon.scimrest.groovy.Script;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;


public interface SearchScriptBuilder {


    SearchScriptBuilder emptyFilterSupported(boolean emptyFilterSupported);

    SearchScriptBuilder implementation(@DelegatesTo(SearchScriptContext.class) @Script.Runtime Closure<?> implementation);

    SearchScriptBuilder supportedFilter(FilterSpecification filterSpec);

    FilterSpecification.Attribute attribute(String name);


}
