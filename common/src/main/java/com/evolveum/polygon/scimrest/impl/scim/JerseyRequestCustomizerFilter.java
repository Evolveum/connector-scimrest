package com.evolveum.polygon.scimrest.impl.scim;

import com.evolveum.polygon.scimrest.api.AuthorizationCustomizer;
import com.evolveum.polygon.scimrest.api.HttpRequestSpecification;
import com.evolveum.polygon.scimrest.config.ScimClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.api.HttpMethod;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.UriBuilder;

import java.io.IOException;

public class JerseyRequestCustomizerFilter implements ClientRequestFilter {

    private final AuthorizationCustomizer<ScimClientConfiguration> customizer;
    private final ScimClientConfiguration configuration;

    public JerseyRequestCustomizerFilter(AuthorizationCustomizer<ScimClientConfiguration> customizer,
                                         ScimClientConfiguration configuration) {
        this.customizer = customizer;
        this.configuration = configuration;
    }

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        var dto = new HttpRequestSpecification(configuration.getScimBaseUrl());
        try {
            dto.httpMethod(HttpMethod.valueOf(requestContext.getMethod()));
        } catch (IllegalArgumentException ignored) {
            // non-standard HTTP method, leave DTO default
        }

        customizer.customize(configuration, dto);

        dto.getHeaders().forEach((name, values) ->
            values.forEach(v -> requestContext.getHeaders().add(name, v))
        );

        if (!dto.getQueryParameters().isEmpty()) {
            var uriBuilder = UriBuilder.fromUri(requestContext.getUri());
            dto.getQueryParameters().forEach(uriBuilder::queryParam);
            requestContext.setUri(uriBuilder.build());
        }

        requestContext.setMethod(dto.getHttpMethod().name());

        if (dto.getBody() != null) {
            requestContext.setEntity(dto.getBody());
        }
    }
}
