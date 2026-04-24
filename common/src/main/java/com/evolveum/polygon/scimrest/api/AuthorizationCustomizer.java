/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.api;

import com.evolveum.polygon.scimrest.config.ConfigurationMixin;

/**
 * Defines a mechanism for customizing the authorization of HTTP requests.
 * Implementations of this interface are used to modify the request details
 * based on the provided configuration to add specific authorization settings.
 *
 * @param <C> the type of configuration used for customization
 */
public interface AuthorizationCustomizer<C extends ConfigurationMixin> {

    /**
     * Customizes the HTTP request to apply specific authorization logic or additional settings.
     *
     * @param configuration the configuration that provides details like base address and authorization settings
     * @param request       the HTTP request builder that can be modified to include custom headers,
     *                      parameters, or other request configurations
     */
    void customize(C configuration, HttpRequestDTO request);

}
