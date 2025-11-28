/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;


public interface JsonValueMapping extends ValueMapping<Object, JsonNode> {

    JsonNodeFactory NODE_FACTORY = new JsonNodeFactory(true);
}
