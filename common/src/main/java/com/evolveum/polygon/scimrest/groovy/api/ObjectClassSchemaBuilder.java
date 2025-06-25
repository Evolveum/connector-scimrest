/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy.api;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public interface ObjectClassSchemaBuilder {

    ObjectClassSchemaBuilder description(String description);

    /**
     * Creates / gets attribute definition with the specified name.
     *
     * @param name the name of the attribute to be configured
     * @return an instance of {@link RestAttributeBuilder} for further configuration of the attribute
     */
    RestAttributeBuilder attribute(String name);

    /**
     * Creates / gets reference definition with the specified name.
     *
     * @param name the name of the reference attribute to be configured
     * @return an instance of {@link RestReferenceAttributeBuilder} for further configuration of the reference attribute
     */
    RestReferenceAttributeBuilder reference(String name);

    /**
     * Creates or gets an attribute definition with the specified name, applying a closure to further configure it.
     *
     * @param name the name of the attribute to be configured
     * @param closure a closure that configures the {@link RestAttributeBuilder} instance for the specified attribute
     * @return an instance of {@link RestAttributeBuilder} for further configuration of the attribute
     */
    RestAttributeBuilder attribute(String name, @DelegatesTo(value = RestAttributeBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure);

    /**
     * Creates / gets reference definition with the specified name, applying a closure to further configure it.
     *
     * @param name the name of the reference attribute to be configured
     * @param closure a closure that configures the {@link RestReferenceAttributeBuilder} instance for the specified reference attribute
     * @return an instance of {@link RestReferenceAttributeBuilder} for further configuration of the reference attribute
     */
    RestReferenceAttributeBuilder reference(String name, @DelegatesTo(RestReferenceAttributeBuilder.class) Closure<?> closure);

    /**
     * Returns the SCIM mapping configuration for this object class.
     *
     * @return an instance of {@link ScimMapping} representing the SCIM schema mappings
     */
    ScimMapping scim();

    /**
     * Creates or gets an SCIM mapping configuration using a closure to further configure it.
     *
     * @param closure a closure that configures the {@link ScimMapping} instance
     * @return an instance of {@link ScimMapping} representing the SCIM schema mappings
     */
    ScimMapping scim(@DelegatesTo(value = ScimMapping.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure);

    /**
     * Defines the SCIM (System for Cross-domain Identity Management) mapping configuration.
     *
     * This interface represents a SCIM schema mappings that can be used to configure how attributes are mapped from/to an external system.
     * It provides methods to specify extensions, set the schema URI, define the name of the SCIM Resource,
     * and control whether only explicitly listed attributes should be considered.
     */
    interface ScimMapping {

        ScimMapping extension(String alias, String namespace);

        /**
         * Retrieves the URI of the SCIM schema associated with this mapping configuration.
         *
         * @return The URI identifying the SCIM schema.
         */
        String schemaUri();

        /**
         * Name of SCIM Resource which this object class represents.
         *
         * The name is used to match resource to this object calss.
         * @return name of SCIM Resource
         */
        String name();

        /**
         * Name of SCIM Resource which this object class represents.
         *
         * The name is used to match resource to this object class.
         *
         * @return The current ScimMapping instance for method chaining.
         */
        ScimMapping name(String name);

        boolean isOnlyExplicitlyListed();

        /**
         * Configures whether only explicitly listed attributes should be added to object class with  SCIM mapping.
         *
         * @param value If true, only explicitly listed attributes will be considered. Otherwise, all attributes will be added.
         * @return The current ScimMapping instance for method chaining.
         */
        ScimMapping onlyExplicitlyListed(boolean value);

        String extensionUriFromAlias(String uriOrAlias);
    }


}
