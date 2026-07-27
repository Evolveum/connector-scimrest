/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.scim;

import com.unboundid.scim2.common.utils.JsonUtils;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientResponseContext;
import jakarta.ws.rs.client.ClientResponseFilter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Restores the RFC 7643 §7 default values ({@code multiValued=false}, {@code required=false},
 * {@code mutability=readWrite}, {@code returned=default}) on {@code /Schemas} responses. A SCIM
 * server may correctly omit these attribute characteristics when they hold their default value, but
 * the SCIM2 SDK's {@code AttributeDefinition} Jackson creator incorrectly marks all four as required,
 * throwing {@code MismatchedInputException} instead of applying the RFC-defined default
 * (<a href="https://github.com/pingidentity/scim2/issues/309">pingidentity/scim2#309</a>).
 */
public class ScimSchemaDefaultsFilter implements ClientResponseFilter {

    @Override
    public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) throws IOException {
        if (!requestContext.getUri().getPath().endsWith("/Schemas") || responseContext.getStatus() >= 300
                || !responseContext.hasEntity()) {
            return;
        }

        var root = JsonUtils.getObjectReader().readTree(responseContext.getEntityStream());
        patch(root);

        var patched = JsonUtils.getObjectWriter().writeValueAsBytes(root);
        responseContext.getHeaders().putSingle("Content-Length", String.valueOf(patched.length));
        responseContext.setEntityStream(new ByteArrayInputStream(patched));
    }

    private void patch(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            var object = (ObjectNode) node;
            applyDefaults(object, "attributes");
            applyDefaults(object, "subAttributes");
        }
        node.forEach(this::patch);
    }

    private void applyDefaults(ObjectNode parent, String fieldName) {
        var attributes = parent.get(fieldName);
        if (attributes == null || !attributes.isArray()) {
            return;
        }
        for (var attribute : attributes) {
            if (!attribute.isObject()) {
                continue;
            }
            var a = (ObjectNode) attribute;
            if (!a.has("multiValued")) {
                a.put("multiValued", false);
            }
            if (!a.has("required")) {
                a.put("required", false);
            }
            if (!a.has("mutability")) {
                a.put("mutability", "readWrite");
            }
            if (!a.has("returned")) {
                a.put("returned", "default");
            }
        }
    }
}
