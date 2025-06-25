/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy.api;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public interface OperationSupportBuilder {

    SearchOperationBuilder search(@DelegatesTo(SearchOperationBuilder.class) Closure<?> o);

}
