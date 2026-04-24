package com.evolveum.polygon.scimrest.groovy;

import com.evolveum.polygon.scimrest.api.AuthorizationCustomizer;
import com.evolveum.polygon.scimrest.api.HttpRequestDTO;
import com.evolveum.polygon.scimrest.config.RestClientConfiguration;

import java.util.HashMap;
import java.util.Map;

public class DispatchingAuthorizationCustomizer implements AuthorizationCustomizer<RestClientConfiguration> {

    Map<Class<? extends RestClientConfiguration>, AuthorizationCustomizer<RestClientConfiguration>> customizers = new HashMap<>();

    @Override
    public void customize(RestClientConfiguration configuration, HttpRequestDTO request) {
        for (var customizer : customizers.entrySet()) {
            if (RestClientConfiguration.isConfigured(customizer.getKey(), configuration)) {
                customizer.getValue().customize(configuration, request);
            }
        }
    }

    public void addCustomizer(Class<? extends RestClientConfiguration> clazz, AuthorizationCustomizer<RestClientConfiguration> customizer) {
        customizers.put(clazz, customizer);
    }
}
