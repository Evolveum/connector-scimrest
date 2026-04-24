/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.api;

import com.evolveum.polygon.scimrest.groovy.api.HttpMethod;
import com.evolveum.polygon.scimrest.groovy.api.HttpVersion;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Protocol-agnostic data-transfer object that carries the full configuration of a
 * single outgoing HTTP request.
 *
 * <p>The DTO itself performs no I/O and has no dependency on any specific HTTP client
 * library. It is intended to be populated by the caller and then handed to an
 * {@link HttpRequestConverter} implementation that translates it into the native
 * request type of the chosen client (e.g. {@code java.net.http.HttpRequest}).
 *
 * <p>All setter-style methods return {@code this} to support a fluent builder style:
 * <pre>{@code
 * var dto = new HttpRequestDTO(baseUrl)
 *         .httpMethod(HttpMethod.POST)
 *         .apiEndpoint("/users")
 *         .header("Content-Type", "application/json")
 *         .body(jsonBytes)
 *         .timeout(Duration.ofSeconds(30));
 * }</pre>
 */
public class HttpRequestDTO {

    private final String baseUri;
    private HttpMethod httpMethod = HttpMethod.GET;
    private String apiEndpoint;
    private final StringBuilder subpath = new StringBuilder();
    private final Map<String, String> queryParameters = new LinkedHashMap<>();
    private final Map<String, Object> pathParameters = new LinkedHashMap<>();
    private final Map<String, List<String>> headers = new LinkedHashMap<>();
    private byte[] body;
    private Duration timeout;
    private HttpVersion version;
    private boolean expectContinue = false;

    public HttpRequestDTO(String baseUri) {
        this.baseUri = baseUri;
    }

    public HttpRequestDTO timeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    public HttpRequestDTO subpath(String path) {
        if (path != null && !path.isEmpty()) {
            if (this.subpath.length() > 0 || path.replace("/", "").length() > 0) {
                this.subpath.append("/").append(path.startsWith("/") ? path.substring(1) : path);
            }
        }
        return this;
    }

    public HttpRequestDTO apiEndpoint(String endpoint) {
        this.apiEndpoint = endpoint;
        return this;
    }

    public HttpRequestDTO httpMethod(HttpMethod method) {
        this.httpMethod = method;
        return this;
    }

    public HttpRequestDTO query(String key, Object value) {
        if (value != null) {
            this.queryParameters.put(key, value.toString());
        }
        return this;
    }

    public HttpRequestDTO queryParameter(String key, Object value) {
        return query(key, value);
    }

    public HttpRequestDTO pathParameter(String key, Object value) {
        if (value != null) {
            this.pathParameters.put(key, value);
        }
        return this;
    }

    public HttpRequestDTO formParam(String key, String value) {
        if (value != null) {
            String pair = urlEncode(key) + "=" + urlEncode(value);
            String current = body != null ? new String(body, StandardCharsets.UTF_8) : "";
            body = (current.isEmpty() ? pair : current + "&" + pair).getBytes(StandardCharsets.UTF_8);
        }
        return this;
    }

    public HttpRequestDTO header(String name, String value) {
        if (value != null) this.headers.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
        return this;
    }

    public HttpRequestDTO body(byte[] body) {
        this.body = body;
        return this;
    }

    public HttpRequestDTO body(String body) {
        this.body = body != null ? body.getBytes(StandardCharsets.UTF_8) : null;
        return this;
    }

    public HttpRequestDTO version(HttpVersion version) {
        this.version = version;
        return this;
    }

    public HttpRequestDTO expectContinue(boolean expectContinue) {
        this.expectContinue = expectContinue;
        return this;
    }

    private String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    public String getBaseUri() {
        return baseUri;
    }

    public HttpMethod getHttpMethod() {
        return httpMethod;
    }

    public String getApiEndpoint() {
        return apiEndpoint;
    }

    public String getSubpath() {
        return subpath.toString();
    }

    public Map<String, String> getQueryParameters() {
        return Collections.unmodifiableMap(queryParameters);
    }

    public Map<String, Object> getPathParameters() {
        return Collections.unmodifiableMap(pathParameters);
    }

    public Map<String, List<String>> getHeaders() {
        return Collections.unmodifiableMap(headers);
    }

    public byte[] getBody() {
        return body;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public HttpVersion getVersion() {
        return version;
    }

    public boolean isExpectContinue() {
        return expectContinue;
    }
}
