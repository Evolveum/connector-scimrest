/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 */
package com.evolveum.polygon.scimrest.config;

import org.identityconnectors.framework.common.exceptions.ConfigurationException;

/**
Identifies a relationship declaration in configuration errors. Missing values are reported explicitly instead of inferred.
*/
public record RelationshipContext(String attribute, String endpoint) {

    /**
    Describes the attribute and endpoint known at the point of failure.
    */
    public String description() {
        return "Relationship records for attribute '" + display(attribute) + "' at endpoint '" + display(endpoint) + "'";
    }

    /**
    Creates a configuration failure carrying this declaration's context.
    */
    public ConfigurationException invalid(String detail) {
        return invalid(detail, null);
    }

    /**
    Creates a contextual configuration failure while preserving the original cause.
    */
    public ConfigurationException invalid(String detail, Throwable cause) {
        return new ConfigurationException(description() + ": " + detail, cause);
    }

    private static String display(String value) {
        return value == null || value.isBlank() ? "<not configured>" : value;
    }
}
