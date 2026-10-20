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
import com.evolveum.polygon.scimrest.groovy.api.EndpointBuilder;

/**
 * Binds a request block's {@code headers:} key — a mapping of header name to a
 * scalar value (a {@code null} entry adds nothing) — onto the endpoint's request builder via
 * {@code header(name, value)}. The values are static: they are sent with every request the
 * endpoint issues.
 */
public class HeadersHandler implements CustomYamlHandler {

    @Override
    public void apply(DeclYamlBinder binder, Object target, LocatedNode value) {
        if (value == null || value.isNull()) {
            return; // no headers declared
        }
        EndpointBuilder.RequestBuilder<?> requestBuilder = null;
        EndpointBuilder.QueryRequestBuilder<?> queryRequestBuilder = null;
        switch (target) {
            case EndpointBuilder.RequestBuilder<?> rb -> requestBuilder = rb;
            case EndpointBuilder.QueryRequestBuilder<?> qrb -> queryRequestBuilder = qrb;
            case null, default -> throw new IllegalArgumentException("The 'headers' key requires a request builder, got: "
                    + target.getClass().getName());
        }
        if (value.kind() != LocatedNode.Kind.OBJECT) {
            throw new IllegalArgumentException("The 'headers' key requires a mapping of header"
                    + " name to scalar value at " + value.line() + ":" + value.col());
        }
        for (var entry : value.entries()) {
            var entryValue = entry.value();
            if (entryValue.isNull()) {
                continue; // a null value adds nothing
            }
            if (!entryValue.isValue()) {
                throw new IllegalArgumentException("The header '" + entry.key()
                        + "' requires a scalar value at " + entryValue.line() + ":" + entryValue.col());
            }
            if (requestBuilder != null) {
                requestBuilder.header(entry.key(), entryValue.text());
            } else {
                queryRequestBuilder.header(entry.key(), entryValue.text());
            }
        }
    }
}
