/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.yaml.model;

import java.util.List;

public class YamlWriteEndpoint {

    public String method;
    public String path;
    public YamlRequest request;
    public List<YamlSupportedAttribute> supportedAttributes;
}
