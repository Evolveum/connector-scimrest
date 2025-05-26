package com.evolveum.polygon.scim.rest.groovy.api;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public interface OperationSupportBuilder {

    SearchOperationBuilder search(@DelegatesTo(SearchOperationBuilder.class) Closure<?> o);

}
