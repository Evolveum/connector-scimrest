/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scim.rest.config;

public interface GroovyConfiguration {


    default <T extends ConfigurationMixin> T configuration(Class<T> configurationMixin) {
        if (configurationMixin.isInstance(this)) {
            return configurationMixin.cast(this);
        }
        throw new UnsupportedOperationException("Configuration mixin class " + configurationMixin + " not supported");
    }
}
