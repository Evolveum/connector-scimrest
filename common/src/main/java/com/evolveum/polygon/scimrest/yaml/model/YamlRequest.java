/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.yaml.model;

public class YamlRequest {

    public String contentType;
    public String accept;
    /** Groovy block scalar building the request body. */
    public String body;
}
