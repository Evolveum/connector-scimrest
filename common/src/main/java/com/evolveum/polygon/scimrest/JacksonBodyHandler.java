/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.datatype.jsonorg.JsonOrgModule;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.identityconnectors.framework.common.exceptions.ConnectorException;

/**
 * Jackson-based Body Handler for {@link java.net.http.HttpClient}
 *
 * <p>The optional {@code context} label (e.g. the endpoint and page a response belongs to) is
 * included in parse error messages: the JDK's {@link HttpResponse.ResponseInfo} passed to the
 * body handler does not expose the request URI.</p>
 *
 * @param responseType Supported Response Type one of {@link JSONObject} or {@link JSONArray}
 * @param <T> Body Response Type
 */
public record JacksonBodyHandler<T>(Class<T> responseType, String context) implements HttpResponse.BodyHandler<Object> {

    /**
     * A short label for error messages (e.g. "endpoint /Users, page 2"); may be {@code null}.
     * The JDK's {@link HttpResponse.ResponseInfo} passed to the body handler does not expose
     * the request URI, so the caller provides the context.
     */
    public JacksonBodyHandler(Class<T> responseType) {
        this(responseType, null);
    }

    @Override
    public HttpResponse.BodySubscriber<Object> apply(HttpResponse.ResponseInfo responseInfo) {
        var mapper = JsonMapper.builder().addModule(new JsonOrgModule()).build();
        var status = responseInfo.statusCode();
        var contentType = responseInfo.headers().firstValue("Content-Type").orElse(null);

        if (responseInfo.statusCode() >= 200 && responseInfo.statusCode() < 204) {
                var upstream = HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8);
                return HttpResponse.BodySubscribers.mapping(upstream, m -> {
                    try {
                        var treeNode = mapper.readTree(m);
                        return responseType.cast(treeNode);
                    } catch (JacksonException e) {
                        // The parse position/message of the Jackson error is the useful part —
                        // include it together with where the body came from.
                        throw new ConnectorException(
                                "Failed to parse response body"
                                        + (context == null || context.isBlank() ? "" : " at " + context)
                                        + " (HTTP " + status
                                        + (contentType == null ? "" : ", " + contentType) + "): "
                                        + (e.getMessage() == null || e.getMessage().isBlank()
                                                ? e.getClass().getSimpleName() : e.getMessage()),
                                e);
                    } catch (ClassCastException e) {
                        // A 2xx body that is not the expected JSON node type (e.g. an array where
                        // an object was expected) — the response shape does not match the endpoint.
                        throw new ConnectorException(
                                "Response body"
                                        + (context == null || context.isBlank() ? "" : " at " + context)
                                        + " (HTTP " + status + ") is not a JSON "
                                        + responseType.getSimpleName() + ": " + e.getMessage(), e);
                    }
                });
        }
        // FIXME: Maybe fallback based on returned content type?

        return (HttpResponse.BodySubscriber) HttpResponse.BodySubscribers.ofByteArray();

    }
}
