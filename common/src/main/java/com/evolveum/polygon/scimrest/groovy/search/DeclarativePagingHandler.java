/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy.search;

import com.evolveum.polygon.scimrest.api.HttpRequestSpecification;
import com.evolveum.polygon.scimrest.groovy.api.HttpMethod;
import com.evolveum.polygon.scimrest.groovy.api.PagingInfo;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
        handlePaging(request, pagingInfo, (long) (pagingInfo.getPageOffset() - 1) * pagingInfo.getPageSize());
    }

    /**
    Applies the same parameter mapping with an explicit record offset, for readers advancing by actual response size.
    */
    public void handlePaging(HttpRequestSpecification request, PagingInfo pagingInfo, long offset) {
        for (var parameter : parameters) {
            long value = switch (parameter.token()) {
                case "page" -> pagingInfo.getPageOffset();
                case "offset" -> offset;
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

    /**
    Validates declarative paging tokens and request locations before a collection reader sends requests.
    */
    public static void validate(List<EndpointBasedSearchBuilder.PagingParameter> parameters, HttpMethod method, String path) {
        for (var parameter : parameters) {
            if (!"pageSize".equals(parameter.token()) && !"page".equals(parameter.token())
                    && !"offset".equals(parameter.token())) {
                throw new ConfigurationException(
                        "Unknown paging token '" + parameter.token() + "' in search endpoint '" + path
                                + "' (expected 'pageSize', 'page', or 'offset')");
            }
            if (!"query".equals(parameter.location()) && !"header".equals(parameter.location())
                    && !"body".equals(parameter.location())) {
                throw new ConfigurationException(
                        "Unknown paging location '" + parameter.location() + "' in search endpoint '" + path
                                + "' (expected 'query', 'header', or 'body')");
            }
            if ("body".equals(parameter.location()) && method != HttpMethod.POST) {
                throw new ConfigurationException(
                        "Paging parameter '" + parameter.name() + "' of search endpoint '" + path
                                + "' is mapped onto the request body, which requires the endpoint httpOperation to be POST");
            }
        }
    }

    /**
    Validates offset-based GET paging, parameter names, and collisions with the optional source query parameter.
    Both offset and pageSize tokens are required unless paging is optional and no mappings are declared.
    Throws ConfigurationException before requests are sent when the declaration is invalid.
    */
    public static void validateOffset(List<EndpointBasedSearchBuilder.PagingParameter> parameters, String path, String sourceParameter, boolean required) {
        validate(parameters, HttpMethod.GET, path);
        var tokens = new HashSet<String>();
        var destinations = new HashSet<String>();
        if (sourceParameter != null) {
            validateName(sourceParameter, "sourceParameter", path);
            destinations.add("query:" + sourceParameter);
        }
        for (var parameter : parameters) {
            validateName(parameter.name(), "paging parameter", path);
            if (!tokens.add(parameter.token())) {
                throw invalidOffset(path, "duplicate paging token '" + parameter.token() + "'");
            }
            var name = "header".equals(parameter.location()) ? parameter.name().toLowerCase(Locale.ROOT) : parameter.name();
            if (!destinations.add(parameter.location() + ":" + name)) {
                throw invalidOffset(path, "paging and source parameter destinations must be distinct");
            }
        }
        if ((required || !parameters.isEmpty()) && !tokens.equals(Set.of("offset", "pageSize"))) {
            throw invalidOffset(path, "requires offset and pageSize paging tokens");
        }
    }

    private static void validateName(String parameter, String name, String path) {
        if (parameter == null || !parameter.matches("[A-Za-z_$][A-Za-z0-9_.$~-]*")) {
            throw invalidOffset(path, name + " must be a nonempty request parameter name");
        }
    }

    private static ConfigurationException invalidOffset(String path, String message) {
        return new ConfigurationException("Offset paging for endpoint '" + path + "' " + message);
    }
}
