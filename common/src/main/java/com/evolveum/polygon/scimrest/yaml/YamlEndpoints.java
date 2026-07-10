/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.yaml;

import com.evolveum.polygon.scimrest.groovy.api.EndpointBuilder;
import com.evolveum.polygon.scimrest.groovy.api.HttpMethod;
import com.evolveum.polygon.scimrest.groovy.api.RestUpdateOperationBuilder;
import com.evolveum.polygon.scimrest.yaml.model.YamlRequest;
import com.evolveum.polygon.scimrest.yaml.model.YamlSupportedAttribute;

import java.util.List;
import java.util.Locale;

/**
 * Shared mapping of the directives common to a write (create/update) endpoint: {@code request}
 * (contentType/accept/body) and {@code supportedAttributes}. Both
 * {@link com.evolveum.polygon.scimrest.groovy.api.RestCreateOperationBuilder.Endpoint} and
 * {@link RestUpdateOperationBuilder.Endpoint} extend the same {@code EndpointBuilder.SingleObject} and
 * {@code AttributeSpecific} interfaces, so the configuration is driven uniformly here.
 */
final class YamlEndpoints {

    private static final String EMPTY = "EMPTY";

    private YamlEndpoints() {
    }

    static HttpMethod httpMethod(String name) {
        return HttpMethod.valueOf(name.toUpperCase(Locale.ROOT));
    }

    static void configureRequest(EndpointBuilder.SingleObject<?, ?> ep, YamlRequest request) {
        if (request == null) {
            return;
        }
        var rb = ep.request();
        if (request.contentType != null) {
            rb.contentType(request.contentType);
        }
        if (request.accept != null) {
            rb.accept(request.accept);
        }
        if (EMPTY.equals(request.body)) {
            rb.body(EndpointBuilder.RequestBuilder.EMPTY);
        }
    }

    static void configureSupportedAttributes(
            RestUpdateOperationBuilder.AttributeSpecific<RestUpdateOperationBuilder.AttributeValueFilter, ?> ep,
            List<YamlSupportedAttribute> supportedAttributes) {
        if (supportedAttributes == null) {
            return;
        }
        for (YamlSupportedAttribute attribute : supportedAttributes) {
            var filter = ep.supportedAttribute(attribute.name);
            if (attribute.value != null) {
                filter.value(attribute.value);
            }
            if (attribute.transition != null) {
                filter.transition(attribute.transition.from, attribute.transition.to);
            }
        }
    }
}
