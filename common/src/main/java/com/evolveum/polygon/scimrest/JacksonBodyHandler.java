/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsonorg.JsonOrgModule;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * Jackson-based Body Handler for {@link java.net.http.HttpClient}
 *
 * @param responseType Supported Response Type one of {@link JSONObject} or {@link JSONArray}
 * @param <T> Body Response Type
 */
public record JacksonBodyHandler<T>(Class<T> responseType) implements HttpResponse.BodyHandler<Object> {

    @Override
    public HttpResponse.BodySubscriber<Object> apply(HttpResponse.ResponseInfo responseInfo) {
        var mapper = new ObjectMapper();
        mapper.registerModule(new JsonOrgModule());

        if (responseInfo.statusCode() >= 200 && responseInfo.statusCode() < 204) {
                var upstream = HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8);
                return HttpResponse.BodySubscribers.mapping(upstream, m -> {
                    try {
                        var treeNode = mapper.readTree(m);
                        return responseType.cast(treeNode);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
        }
        // FIXME: Maybe fallback based on returned content type?

        return (HttpResponse.BodySubscriber) HttpResponse.BodySubscribers.ofByteArray();

    }
}