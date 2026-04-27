package com.evolveum.polygon.scimrest.groovy;

import com.evolveum.polygon.scimrest.api.AuthorizationCustomizer;
import com.evolveum.polygon.scimrest.api.HttpRequestSpecification;
import com.evolveum.polygon.scimrest.config.ScimClientConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;

public class DispatchingScimAuthorizationCustomizer implements AuthorizationCustomizer<ScimClientConfiguration> {

    private final Map<Class<? extends ScimClientConfiguration>, AuthorizationCustomizer<ScimClientConfiguration>> customizers = new LinkedHashMap<>();

    @Override
    public void customize(ScimClientConfiguration configuration, HttpRequestSpecification request) {
        for (var entry : customizers.entrySet()) {
            if (configuration.supports(entry.getKey()) != null) {
                entry.getValue().customize(configuration, request);
                return;
            }
        }
    }

    public void addCustomizer(Class<? extends ScimClientConfiguration> clazz, AuthorizationCustomizer<ScimClientConfiguration> customizer) {
        customizers.put(clazz, customizer);
    }
}
