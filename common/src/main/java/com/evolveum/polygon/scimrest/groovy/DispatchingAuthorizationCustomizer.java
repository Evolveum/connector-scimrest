package com.evolveum.polygon.scimrest.groovy;

import com.evolveum.polygon.scimrest.api.AuthorizationCustomizer;
import com.evolveum.polygon.scimrest.api.HttpRequestSpecification;
import com.evolveum.polygon.scimrest.config.RestClientConfiguration;

import java.util.ArrayList;
import java.util.List;

public class DispatchingAuthorizationCustomizer implements AuthorizationCustomizer<RestClientConfiguration> {

    private record Entry(
            Class<? extends RestClientConfiguration> type,
            AuthorizationCustomizer<RestClientConfiguration> customizer,
            boolean skipConfigCheck) {}

    private final List<Entry> entries = new ArrayList<>();

    @Override
    public void customize(RestClientConfiguration configuration, HttpRequestSpecification request) {
        for (var entry : entries) {
            if (applies(entry, configuration)) {
                entry.customizer().customize(configuration, request);
                return;
            }
        }
    }

    public boolean applyForType(Class<? extends RestClientConfiguration> type, RestClientConfiguration configuration, HttpRequestSpecification request) {
        for (var entry : entries) {
            if (entry.type().equals(type) && applies(entry, configuration)) {
                entry.customizer().customize(configuration, request);
                return true;
            }
        }
        return false;
    }

    private boolean applies(Entry entry, RestClientConfiguration configuration) {
        return entry.skipConfigCheck()
                ? entry.type().isInstance(configuration)
                : RestClientConfiguration.isConfigured(entry.type(), configuration);
    }

    public void addBuiltinCustomizer(Class<? extends RestClientConfiguration> clazz, AuthorizationCustomizer<RestClientConfiguration> customizer) {
        entries.removeIf(e -> e.type().equals(clazz) && !e.skipConfigCheck());
        entries.add(new Entry(clazz, customizer, false));
    }

    public void addCustomizer(Class<? extends RestClientConfiguration> clazz, AuthorizationCustomizer<RestClientConfiguration> customizer) {
        entries.removeIf(e -> e.type().equals(clazz));
        entries.add(new Entry(clazz, customizer, true));
    }
}
