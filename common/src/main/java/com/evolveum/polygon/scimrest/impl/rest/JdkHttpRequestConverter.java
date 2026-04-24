/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.rest;

import com.evolveum.polygon.scimrest.api.HttpRequestConverter;
import com.evolveum.polygon.scimrest.api.HttpRequestDTO;
import com.evolveum.polygon.scimrest.groovy.api.HttpVersion;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeUtil;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;

/**
 * Converts an {@link HttpRequestDTO} into a {@link java.net.http.HttpRequest} for use
 * with the JDK's built-in {@code java.net.http.HttpClient}.
 *
 * <p>This converter is responsible for all JDK-specific translation concerns:
 * <ul>
 *   <li>Building the final URI from base URI, API endpoint, path parameters ({@code {placeholder}}
 *       substitution), subpath, and URL-encoded query parameters.</li>
 *   <li>Selecting the appropriate {@link java.net.http.HttpRequest.BodyPublisher} based on the
 *       HTTP method and body bytes from the DTO.</li>
 *   <li>Applying request headers, timeout, HTTP version, and the {@code Expect: 100-continue}
 *       flag.</li>
 * </ul>
 */
public class JdkHttpRequestConverter implements HttpRequestConverter<HttpRequest> {

    @Override
    public HttpRequest convert(HttpRequestDTO dto) {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
        String uriString = buildUriString(dto);
        try {
            requestBuilder.uri(new URI(uriString));
        } catch (URISyntaxException e) {
            throw new ConnectorException("Computed URI: " + uriString + " is not valid", e);
        }

        byte[] body = dto.getBody();
        HttpRequest.BodyPublisher bodyPublisher = body != null
                ? HttpRequest.BodyPublishers.ofByteArray(body)
                : HttpRequest.BodyPublishers.noBody();

        switch (dto.getHttpMethod()) {
            case GET -> requestBuilder.GET();
            case PUT -> requestBuilder.PUT(bodyPublisher);
            case POST -> requestBuilder.POST(bodyPublisher);
            case DELETE -> requestBuilder.DELETE();
            case PATCH -> requestBuilder.method("PATCH", bodyPublisher);
        }

        if (dto.getTimeout() != null) {
            requestBuilder.timeout(dto.getTimeout());
        }

        if (dto.getVersion() != null) {
            requestBuilder.version(dto.getVersion() == HttpVersion.HTTP_2
                    ? HttpClient.Version.HTTP_2
                    : HttpClient.Version.HTTP_1_1);
        }

        requestBuilder.expectContinue(dto.isExpectContinue());

        dto.getHeaders().forEach((name, values) -> {
            for (String value : values) {
                requestBuilder.header(name, value);
            }
        });

        return requestBuilder.build();
    }

    private static String buildUriString(HttpRequestDTO dto) {
        String endpoint = dto.getApiEndpoint();
        if (endpoint != null) {
            for (var entry : dto.getPathParameters().entrySet()) {
                String valStr = entry.getValue() instanceof Attribute a
                        ? AttributeUtil.getAsStringValue(a)
                        : String.valueOf(entry.getValue());
                endpoint = endpoint.replace("{" + entry.getKey() + "}", valStr != null ? valStr : "");
            }
        }
        StringBuilder b = new StringBuilder(dto.getBaseUri());
        if (endpoint != null && !endpoint.isEmpty()) {
            if (b.charAt(b.length() - 1) != '/') b.append("/");
            b.append(endpoint);
        }
        String subpath = dto.getSubpath();
        if (!subpath.isEmpty()) {
            if (b.charAt(b.length() - 1) != '/') b.append("/");
            b.append(subpath.startsWith("/") ? subpath.substring(1) : subpath);
        }
        if (!dto.getQueryParameters().isEmpty()) {
            b.append("?");
            dto.getQueryParameters().forEach((k, v) ->
                    b.append(k).append("=").append(URLEncoder.encode(v, StandardCharsets.UTF_8)).append("&"));
            b.setLength(b.length() - 1);
        }
        return b.toString();
    }
}
