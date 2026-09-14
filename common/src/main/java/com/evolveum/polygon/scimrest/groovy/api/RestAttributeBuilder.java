/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy.api;

import com.evolveum.polygon.conndev.annotations.Script;
import com.evolveum.polygon.conndev.api.AttributePath;
import com.evolveum.polygon.conndev.api.AttributePathDeclaration;
import com.evolveum.polygon.conndev.api.AttributePathFormat;
import com.evolveum.polygon.conndev.build.api.AttributeBuilder;
import com.evolveum.polygon.conndev.annotations.Yaml;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.scimrest.schema.RestAttributeDefinition;
import com.evolveum.polygon.scimrest.schema.ScimPathFormat;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public interface RestAttributeBuilder<F extends RestAttributeBuilder<F>> extends AttributeBuilder<F, RestAttributeDefinition> {

    // Protocol specific mappings
    String name();

    /** The native protocol type as declared by the remote system (e.g. SCIM {@code dateTime}). */
    @Yaml.Key
    F nativeType(String nativeType);

    @Yaml.Sub
    ScimMapping scim();

    ScimMapping scim(@DelegatesTo(value = ScimMapping.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure);

    interface ScimMapping extends MappingBuilder<ScimMapping> {
        /**
         * Name of the matching SCIM attribute
         **/
        String name();

        @Yaml.Key
        ScimMapping name(String name);

        /**
         * The SCIM (JSON) wire type of this attribute, as declared by the SCIM schema or a
         * Groovy definition — or {@code null} if none has been declared yet.
         *
         * @return the declared wire type, or {@code null}
         */
        String type();

        @Yaml.Key
        ScimMapping type(String name);

        /**
         * SCIM Attribute path
         *
         * A path to SCIM attribute which will be mapped to ConnID attribute
         *
         * Path should uniquely point to one JSON attribute (eg. `name.formatted` or `email[type eq primary]`)
         *
         * @param path
         * @return
         */
        @Yaml.Key
        ScimMapping path(String path);

        /**
         * Same as {@link #path(String)}, but carrying the value's origin and source location — the
         * {@code @Yaml.Key} binder prefers this overload so a YAML-declared path that fails SCIM
         * syntax validation reports the exact {@code source:line:col} it was written at, instead of
         * the internal call site.
         *
         * @param path the SCIM attribute path expression, with its origin and location
         * @return this SCIM mapping instance
         */
        ScimMapping path(DefinitionValue<String> path);

        /**
         * SCIM Attribute path
         *
         * A path to SCIM attribute which will be mapped to ConnID attribute
         *
         * Path should uniquely point to one JSON attribute (eg. `name.formatted` or `email[type eq primary]`)
         *
         * @param path
         * @return
         */
        ScimMapping path(AttributePath path);

        /**
         * Sets the SCIM path from a pre-assembled {@link AttributePathDeclaration} (e.g. one built
         * by the declarative YAML front-end for a {@code @Yaml.Path} binding).
         *
         * @param declaration the path declaration
         * @return this SCIM mapping instance
         */
        @Yaml.Path(ScimPathFormat.class)
        ScimMapping path(AttributePathDeclaration<?, ?> declaration);

        default AttributePath attribute(String name) {
            return AttributePath.of(name);
        }

        AttributePath extension(String uriOrAlias);

        AttributePathDeclaration<?,?> path();

        ScimMapping path(
                @DelegatesTo(value = ScimPathBuilder.class, strategy = Closure.DELEGATE_ONLY)
                @Script.Initialization
                Closure<?> closure);
    }

    interface ScimPathBuilder extends PathBuilder {
        AttributePathFormat<String> SCIM = ScimPathFormat.INSTANCE;
    }

}
