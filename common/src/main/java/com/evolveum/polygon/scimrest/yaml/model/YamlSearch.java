/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.yaml.model;

import com.evolveum.polygon.conndev.yaml.model.YamlAttributeResolver;
import com.evolveum.polygon.conndev.yaml.model.YamlCustom;
import com.evolveum.polygon.conndev.yaml.model.YamlNormalize;

import java.util.List;

public class YamlSearch {

    public List<YamlSearchEndpoint> endpoints;
    public YamlNormalize normalize;
    public List<YamlAttributeResolver> attributeResolvers;
    public YamlCustom custom;
}
