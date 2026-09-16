/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.rest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

/**
 * Extracts a short human-readable error detail from an HTTP response body, so that error
 * messages can carry the server's own explanation (e.g. the RFC 7644 {@code detail} field or
 * the RFC 6749 {@code error}/{@code error_description} fields) instead of a bare status code.
 *
 * <p>The body is already consumed at this point (read by the body handler or the response
 * filter); this class only interprets the bytes. Parsing is lenient: anything that is not
 * valid JSON falls back to a truncated raw body, and blank bodies yield {@code null}.</p>
 */
public final class ErrorDetail {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_LENGTH = 512;
    private static final String[] DETAIL_FIELDS = {"detail", "error", "message", "error_description"};

    private ErrorDetail() {
    }

    /**
     * Extracts the error detail from an already-consumed response body.
     *
     * @param body the response body — typically a raw {@code byte[]}, a JSON node or a
     *             {@code String}, or {@code null} for an empty body
     * @return a short (truncated) detail string, or {@code null} when the body carries no
     *         usable text
     */
    public static String extract(Object body) {
        if (body == null) {
            return null;
        }
        if (body instanceof JsonNode node) {
            return fromNode(node);
        }
        String text = body instanceof byte[] bytes
                ? new String(bytes, StandardCharsets.UTF_8)
                : body.toString();
        if (text.isBlank()) {
            return null;
        }
        try {
            JsonNode tree = MAPPER.readTree(text);
            String detail = fromNode(tree);
            if (detail != null) {
                return detail;
            }
        } catch (RuntimeException ignored) {
            // Not JSON (or no detail field) — fall back to the raw body below.
        }
        return truncate(text);
    }

    private static String fromNode(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String field : DETAIL_FIELDS) {
            JsonNode value = node.get(field);
            if (value != null && value.isTextual()) {
                if (sb.length() > 0) {
                    sb.append("; ");
                }
                sb.append(value.asText());
            }
        }
        return sb.length() > 0 ? truncate(sb.toString()) : null;
    }

    private static String truncate(String text) {
        String trimmed = text.strip();
        if (trimmed.length() <= MAX_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_LENGTH) + "...";
    }
}
