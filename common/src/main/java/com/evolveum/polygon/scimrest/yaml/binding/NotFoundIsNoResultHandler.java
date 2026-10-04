/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.yaml.binding;

import com.evolveum.polygon.conndev.yaml.decl.LocatedNode;
import com.evolveum.polygon.conndev.yaml.decl.CustomYamlHandler;
import com.evolveum.polygon.conndev.yaml.decl.DeclYamlBinder;
import com.evolveum.polygon.scimrest.groovy.api.RestSearchEndpointBuilder;

/**
 * Binds the {@code notFoundIsNoResult: true} flag onto a search endpoint, declaring that an HTTP
 * 404 from the endpoint means "no matching objects" (an empty result set) instead of a
 * misconfigured endpoint path.
 */
public class NotFoundIsNoResultHandler implements CustomYamlHandler {

    @Override
    public void apply(DeclYamlBinder binder, Object target, LocatedNode value) {
        if (!(target instanceof RestSearchEndpointBuilder endpoint)) {
            throw new IllegalArgumentException("The 'notFoundIsNoResult' key requires a search endpoint, got: "
                    + target.getClass().getName());
        }
        if (value.isValue() && Boolean.parseBoolean(value.text())) {
            endpoint.notFoundIsNoResult();
        }
    }
}
