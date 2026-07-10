/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.yaml;

import com.evolveum.polygon.scimrest.groovy.BaseOperationSupportBuilder;
import com.evolveum.polygon.scimrest.groovy.api.RestCreateOperationBuilder;
import com.evolveum.polygon.scimrest.yaml.model.YamlOperation;
import com.evolveum.polygon.scimrest.yaml.model.YamlWriteEndpoint;

/** Maps the {@code create} section onto {@link RestCreateOperationBuilder} endpoints. */
final class YamlCreateHandler {

    void load(BaseOperationSupportBuilder objectClass, YamlOperation create) {
        if (create == null || create.endpoints == null) {
            return;
        }
        RestCreateOperationBuilder builder = objectClass.create();
        for (YamlWriteEndpoint endpoint : create.endpoints) {
            RestCreateOperationBuilder.Endpoint ep =
                    builder.endpoint(YamlEndpoints.httpMethod(endpoint.method), endpoint.path);
            YamlEndpoints.configureRequest(ep, endpoint.request);
            YamlEndpoints.configureSupportedAttributes(ep, endpoint.supportedAttributes);
        }
    }
}
