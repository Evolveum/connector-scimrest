/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.scimrest.groovy.api;

public interface GroovyHttpOperationMixin {

    // Redeclared for Groovy API
    HttpMethod GET = HttpMethod.GET;
    HttpMethod POST = HttpMethod.POST;
    HttpMethod PUT = HttpMethod.PUT;
    HttpMethod DELETE = HttpMethod.DELETE;
    HttpMethod PATCH = HttpMethod.PATCH;

}
