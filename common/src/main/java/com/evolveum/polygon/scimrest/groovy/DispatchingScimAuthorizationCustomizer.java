package com.evolveum.polygon.scimrest.groovy;

import com.evolveum.polygon.scimrest.api.AuthorizationCustomizer;
import com.evolveum.polygon.scimrest.api.HttpRequestSpecification;
import com.evolveum.polygon.scimrest.config.ScimClientConfiguration;

import java.util.ArrayList;
import java.util.List;

public class DispatchingScimAuthorizationCustomizer implements AuthorizationCustomizer<ScimClientConfiguration> {

    private record Entry(
            Class<? extends ScimClientConfiguration> type,
            AuthorizationCustomizer<ScimClientConfiguration> customizer,
            boolean skipConfigCheck) {}

    private final List<Entry> entries = new ArrayList<>();

    @Override
    public void customize(ScimClientConfiguration configuration, HttpRequestSpecification request) {
        for (var entry : entries) {
            if (applies(entry, configuration)) {
                entry.customizer().customize(configuration, request);
                return;
            }
        }
    }

    public boolean applyForType(Class<? extends ScimClientConfiguration> type, ScimClientConfiguration configuration, HttpRequestSpecification request) {
        for (var entry : entries) {
            if (entry.type().equals(type) && applies(entry, configuration)) {
                entry.customizer().customize(configuration, request);
                return true;
            }
        }
        return false;
    }

    private boolean applies(Entry entry, ScimClientConfiguration configuration) {
        return entry.skipConfigCheck()
                ? entry.type().isInstance(configuration)
                : ScimClientConfiguration.isConfigured(entry.type(), configuration);
    }

    public void addBuiltinCustomizer(Class<? extends ScimClientConfiguration> clazz, AuthorizationCustomizer<ScimClientConfiguration> customizer) {
        entries.removeIf(e -> e.type().equals(clazz) && !e.skipConfigCheck());
        entries.add(new Entry(clazz, customizer, false));
    }

    public void addCustomizer(Class<? extends ScimClientConfiguration> clazz, AuthorizationCustomizer<ScimClientConfiguration> customizer) {
        entries.removeIf(e -> e.type().equals(clazz) && e.skipConfigCheck());
        entries.add(new Entry(clazz, customizer, true));
    }
}
