/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 */
package com.evolveum.polygon.scimrest.groovy.api;

import com.evolveum.polygon.conndev.annotations.Yaml;
import com.evolveum.polygon.scimrest.yaml.binding.RelationshipRecordsHandler;

/**
Exposes relationship declarations to the shared YAML binder without coupling retrieval and update implementations.
*/
public interface RelationshipOperationBuilder<Builder> {

    /**
    Selects the attribute's relationship declaration. YAML failures retain its attribute and endpoint context.
    */
    @Yaml.Custom(RelationshipRecordsHandler.class)
    Builder relationshipRecords(String attribute);
}
