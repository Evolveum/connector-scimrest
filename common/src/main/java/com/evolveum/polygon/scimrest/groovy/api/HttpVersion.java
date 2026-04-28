/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy.api;

/**
 * Protocol version to use for an HTTP request.
 *
 * <p>This enum is intentionally independent of any specific HTTP client library so that
 * the {@code api} package does not carry a runtime dependency on {@code java.net.http}.
 * Converter implementations (e.g. {@code JdkHttpRequestConverter}) are responsible for
 * mapping these values to the corresponding client-specific constants.
 */
public enum HttpVersion {
    HTTP_1_1,
    HTTP_2
}
