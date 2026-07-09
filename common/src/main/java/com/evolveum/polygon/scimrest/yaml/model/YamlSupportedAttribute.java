/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.yaml.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public class YamlSupportedAttribute {

    public String name;
    public Object value;
    public YamlTransition transition;

    /** Allows the short form: a plain string item declares just the attribute name. */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    static YamlSupportedAttribute of(String name) {
        YamlSupportedAttribute attribute = new YamlSupportedAttribute();
        attribute.name = name;
        return attribute;
    }
}
