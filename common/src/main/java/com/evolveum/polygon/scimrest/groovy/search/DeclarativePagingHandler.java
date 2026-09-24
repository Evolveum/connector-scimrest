/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy.search;

import com.evolveum.polygon.scimrest.api.HttpRequestSpecification;
import com.evolveum.polygon.scimrest.groovy.api.PagingInfo;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;

import java.util.List;

/**
 * A {@link PagingHandler} built from declarative paging parameters instead of a Groovy closure:
 * each parameter maps a paging token onto the request as a query parameter, header, or request
 * body member. The tokens are {@code pageSize} (the requested page size), {@code page} (the
 * page number, starting with 1), and {@code offset} (the zero-based object offset,
 * {@code (page - 1) * pageSize}).
 */
public record DeclarativePagingHandler(List<EndpointBasedSearchBuilder.PagingParameter> parameters) implements PagingHandler {

    @Override
    public void handlePaging(HttpRequestSpecification request, PagingInfo pagingInfo) {
        for (var parameter : parameters) {
            long value = switch (parameter.token()) {
                case "page" -> pagingInfo.getPageOffset();
                case "offset" -> (long) (pagingInfo.getPageOffset() - 1) * pagingInfo.getPageSize();
                default -> pagingInfo.getPageSize(); // pageSize
            };
            switch (parameter.location()) {
                case "query" -> request.queryParameter(parameter.name(), value);
                case "header" -> request.header(parameter.name(), String.valueOf(value));
                case "body" -> request.bodyParameter(parameter.name(), value);
                default -> throw new ConfigurationException(
                        "Unknown paging parameter location '" + parameter.location() + "' (expected query, header, or body)");
            }
        }
    }
}
