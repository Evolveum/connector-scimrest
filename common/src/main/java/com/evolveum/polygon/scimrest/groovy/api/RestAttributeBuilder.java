/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy.api;

import com.evolveum.polygon.conndev.api.AttributePath;
import com.evolveum.polygon.conndev.build.api.AttributeBuilder;
import com.evolveum.polygon.scimrest.schema.MappedAttribute;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public interface RestAttributeBuilder<F extends RestAttributeBuilder<F>> extends AttributeBuilder<F, MappedAttribute> {

    // Protocol specific mappings

    ScimMapping scim();

    ScimMapping scim(@DelegatesTo(value = ScimMapping.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure);

    interface ScimMapping extends MappingBuilder<ScimMapping> {
        /**
         * Name of the matching SCIM attribute
         **/
        String name();

        ScimMapping name(String name);

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
        ScimMapping path(String path);

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

        default AttributePath attribute(String name) {
            return AttributePath.of(name);
        }

        AttributePath extension(String uriOrAlias);

        AttributePath path();
    }

}
