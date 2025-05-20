package com.evolveum.polygon.scim.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsonorg.JsonOrgModule;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpResponse;

public record JacksonBodyHandler<T>(Class<T> responseType) implements HttpResponse.BodyHandler<T> {

    @Override
    public HttpResponse.BodySubscriber<T> apply(HttpResponse.ResponseInfo responseInfo) {
        var mapper = new ObjectMapper();
        mapper.registerModule(new JsonOrgModule());
        var upstream = HttpResponse.BodySubscribers.ofInputStream();
        return HttpResponse.BodySubscribers.mapping(upstream, m -> {
            try {
                return mapper.readValue(m, responseType);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}