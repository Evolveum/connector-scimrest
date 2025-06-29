/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy;


import com.evolveum.polygon.scimrest.impl.rest.RestContext;
import com.evolveum.polygon.scimrest.groovy.api.PagingInfo;

public interface PagingHandler {

    void handlePaging(RestContext.RequestBuilder builder, PagingInfo pagingInfo);

}
