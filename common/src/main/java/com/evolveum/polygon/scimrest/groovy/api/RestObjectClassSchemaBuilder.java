/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy.api;

import com.evolveum.polygon.conndev.build.api.ObjectClassSchemaBuilder;
import com.evolveum.polygon.conndev.annotations.Yaml;
import com.evolveum.polygon.scimrest.yaml.binding.ScimExtensionMappingHandler;
import com.evolveum.polygon.scimrest.yaml.binding.ScimFlattenListHandler;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

import java.util.List;

public interface RestObjectClassSchemaBuilder extends ObjectClassSchemaBuilder<
        RestObjectClassSchemaBuilder, RestAttributeBuilder<RestReferenceAttributeBuilder>, RestReferenceAttributeBuilder> {

    /**
     * @return {@code true} if this object class is declared as cached.
     */
    boolean isCached();

    /**
     * Declares this object class as cached: it is loaded lazily through its declared
     * search operation, and the resulting objects are stored per connector instance
     * for use by {@code lookup} value resolution of other object classes.
     *
     * @param value {@code true} to mark the object class as cached
     * @return this object class builder
     */
    @Yaml.Key
    RestObjectClassSchemaBuilder cached(boolean value);

    /**
     * Returns the SCIM mapping configuration for this object class.
     *
     * @return an instance of {@link ScimMapping} representing the SCIM schema mappings
     */
    @Yaml.Sub
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
    /**
     * A SCIM extension declared on an object class that carries a non-empty flatten list:
     * the complex attributes listed in {@code flatten} are decomposed into plain attributes of
     * the object class (see {@link ScimMapping#extension(String, String, Closure)}).
     *
     * @param alias the alias the extension is declared under
     * @param extensionUri the schema URI of the SCIM extension
     * @param flatten the SCIM attribute names to flatten (in declaration order, never empty)
     */
    record ExtensionFlattening(String alias, String extensionUri, List<String> flatten) {
    }

    interface ScimMapping {

        ScimMapping extension(String alias, String namespace);

        /**
         * Declares a SCIM extension and configures which of its complex attributes are flattened
         * into plain attributes of this object class (the closure configures an
         * {@link ExtensionMapping}).
         *
         * <p>The flattened attributes are named after the extension alias
         * (e.g. {@code enterprise_work_photo} for {@code photos} of extension
         * {@code enterprise}) and carry SCIM paths qualified with the extension schema URI —
         * the extension counterpart of {@link #flatten(String)}.
         *
         * @param alias the short alias the extension is referenced by (e.g. {@code "enterprise"})
         * @param namespace the schema URI of the SCIM extension
         * @param closure a closure that configures the {@link ExtensionMapping}
         * @return the configured {@link ExtensionMapping}
         */
        ExtensionMapping extension(String alias, String namespace,
                                   @DelegatesTo(value = ExtensionMapping.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure);

        /**
         * Declares a SCIM extension with its set of complex attributes to flatten
         * (the non-closure counterpart of {@link #extension(String, String, Closure)},
         * used by the YAML binding).
         *
         * @param alias the short alias the extension is referenced by
         * @param namespace the schema URI of the SCIM extension
         * @param flattenAttributes the SCIM attribute names to flatten (may be empty)
         * @return The current ScimMapping instance for method chaining.
         */
        ScimMapping extension(String alias, String namespace, List<String> flattenAttributes);

        /**
         * The extensions of this object class that carry a non-empty flatten list
         * (in declaration order; empty when none of the declared extensions lists any).
         */
        List<ExtensionFlattening> extensionFlattens();

        /**
         * Marker for the YAML {@code extensions:} key of the object-class SCIM mapping — the
         * YAML counterpart of the {@code extension(alias, namespace) { ... }} calls: a map of
         * extension aliases to a schema URI (or to a mapping with a required {@code uri} and an
         * optional {@code flatten} list). Bound by {@link ScimExtensionMappingHandler}; the
         * method body is unused.
         */
        @Yaml.Custom(ScimExtensionMappingHandler.class)
        default void extensions() {
        }

        /**
         * The configuration of a single declared SCIM extension (the delegate of the
         * {@code scim { extension(...) { ... } }} closure).
         */
        interface ExtensionMapping {

            /**
             * Adds a complex attribute of this extension to the flatten set: the attribute is
             * decomposed into plain attributes of the containing object class, named after the
             * extension alias (e.g. {@code enterprise_work_photo}) and carrying SCIM paths
             * qualified with the extension schema URI.
             *
             * <p>Unlike the object-class {@link ScimMapping#flatten(String)} set, the extension
             * list is always resolved generically against the extension's schema: the
             * well-known families and the connector-level {@code SCIM Mapping} flatten
             * properties do not apply. A listed multi-valued attribute must carry a scalar
             * {@code type} sub-attribute.
             *
             * @param attribute the SCIM attribute name of the extension to flatten (e.g. {@code "photos"})
             * @return The current ExtensionMapping instance for method chaining.
             */
            ExtensionMapping flatten(String attribute);

            /**
             * The complex attributes of this extension configured to be flattened
             * (SCIM attribute names, in declaration order; empty when none were listed).
             */
            List<String> flattenAttributes();
        }

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
        @Yaml.Key
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
        @Yaml.Key
        ScimMapping name(String name);

        boolean isOnlyExplicitlyListed();

        /**
         * Configures whether only explicitly listed attributes should be added to object class with  SCIM mapping.
         *
         * @param value If true, only explicitly listed attributes will be considered. Otherwise, all attributes will be added.
         * @return The current ScimMapping instance for method chaining.
         */
        @Yaml.Key
        ScimMapping onlyExplicitlyListed(boolean value);

        /**
         * Adds a complex attribute to the per-object-class flatten set: the attribute is
         * decomposed into plain attributes of this object class instead of an embedded object class.
         *
         * <p>The set is additive to the connector-level {@code SCIM Mapping} flatten properties —
         * a listed family is flattened for this object class even when the corresponding
         * connector property is off, while unlisted families still follow the connector
         * properties. The list therefore extends, but never restricts, the configured flattening.
         *
         * <p>The well-known families {@code name}, {@code emails}, {@code phoneNumbers} and
         * {@code addresses} are always available; any other complex attribute of the resource's
         * primary SCIM schema can be listed as well (a multi-valued one must carry a scalar
         * {@code type} sub-attribute).
         *
         * @param attribute the SCIM attribute name to flatten (e.g. {@code "name"}, {@code "emails"})
         * @return The current ScimMapping instance for method chaining.
         */
        @Yaml.Custom(ScimFlattenListHandler.class)
        ScimMapping flatten(String attribute);

        /**
         * The complex attributes configured to be flattened for this object class
         * (SCIM attribute names, in declaration order; empty when none were listed).
         */
        List<String> flattenAttributes();

        String extensionUriFromAlias(String uriOrAlias);
    }


}
