/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scim.rest.groovy;


import com.evolveum.polygon.scim.rest.RestContext;
import com.evolveum.polygon.scim.rest.groovy.api.PagingInfo;

public interface PagingHandler {

    void handlePaging(RestContext.RequestBuilder builder, PagingInfo pagingInfo);

}
