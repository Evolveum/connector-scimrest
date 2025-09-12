package com.evolveum.polygon.scimrest.groovy;

import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.impl.rest.RestContext;
import com.evolveum.polygon.scimrest.impl.rest.RestContext.AuthorizationCustomizer;


import java.util.HashMap;
import java.util.Map;

public class DispatchingAuthorizationCustomizer implements AuthorizationCustomizer {

    Map<Class<? extends RestClientConfiguration>, AuthorizationCustomizer> customizers = new HashMap<Class<? extends RestClientConfiguration>, AuthorizationCustomizer>();

    @Override
    public void customize(RestClientConfiguration configuration, RestContext.RequestBuilder request) {
        for (var customizer : customizers.entrySet()) {
            if (RestClientConfiguration.isConfigured(customizer.getKey(), configuration)) {
                customizer.getValue().customize(configuration, request);
            }
        }
    }

    public void addCustomizer(Class<? extends RestClientConfiguration> clazz, AuthorizationCustomizer customizer) {
        customizers.put(clazz, customizer);
    }
}
