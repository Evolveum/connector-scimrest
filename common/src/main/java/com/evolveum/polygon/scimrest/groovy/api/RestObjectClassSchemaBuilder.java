/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy.api;

import com.evolveum.polygon.conndev.build.api.ObjectClassSchemaBuilder;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public interface RestObjectClassSchemaBuilder extends ObjectClassSchemaBuilder<
        RestObjectClassSchemaBuilder, RestAttributeBuilder<RestReferenceAttributeBuilder>, RestReferenceAttributeBuilder> {

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
         * Sets the URI of the SCIM schema associated with this mapping configuration.
         *
         * @param schemaUri The URI identifying the SCIM schema.
         * @return The current ScimMapping instance for method chaining.
         */
        ScimMapping schemaUri(String schemaUri);

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
