/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.api;

/**
 * Converts an {@link HttpRequestSpecification} into a concrete HTTP request object suitable
 * for a specific HTTP client library.
 *
 * <p>Implementations are responsible for translating the protocol-agnostic data
 * held in the DTO (URI, method, headers, body, path/query parameters, etc.) into
 * the native request type {@code T} expected by the target HTTP client.
 *
 * <p>Example implementations:
 * <ul>
 *   <li>{@code JdkHttpRequestConverter} — produces {@code java.net.http.HttpRequest}</li>
 * </ul>
 *
 * @param <T> the type of the concrete HTTP request produced by this converter
 */
@FunctionalInterface
public interface HttpRequestConverter<T> {

    /**
     * Converts the given {@link HttpRequestSpecification} to a concrete HTTP request.
     *
     * @param dto the data-transfer object carrying all request configuration
     * @return a fully configured HTTP request of type {@code T}
     */
    T convert(HttpRequestSpecification dto);

}
