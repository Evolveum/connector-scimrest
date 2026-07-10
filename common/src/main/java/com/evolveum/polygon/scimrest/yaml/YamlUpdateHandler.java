/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.yaml;

import com.evolveum.polygon.scimrest.groovy.BaseOperationSupportBuilder;
import com.evolveum.polygon.scimrest.groovy.api.RestUpdateOperationBuilder;
import com.evolveum.polygon.scimrest.yaml.model.YamlOperation;
import com.evolveum.polygon.scimrest.yaml.model.YamlWriteEndpoint;

/** Maps the {@code update} section onto {@link RestUpdateOperationBuilder} endpoints. */
final class YamlUpdateHandler {

    void load(BaseOperationSupportBuilder objectClass, YamlOperation update) {
        if (update == null || update.endpoints == null) {
            return;
        }
        RestUpdateOperationBuilder builder = objectClass.update();
        for (YamlWriteEndpoint endpoint : update.endpoints) {
            RestUpdateOperationBuilder.Endpoint ep =
                    builder.endpoint(YamlEndpoints.httpMethod(endpoint.method), endpoint.path);
            YamlEndpoints.configureRequest(ep, endpoint.request);
            YamlEndpoints.configureSupportedAttributes(ep, endpoint.supportedAttributes);
        }
    }
}
