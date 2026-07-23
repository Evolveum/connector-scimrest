/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.schema;

import com.evolveum.polygon.conndev.schema.BaseAttributeDefinition;
import com.evolveum.polygon.scimrest.groovy.api.AttributeResolver;

public class MappedAttribute extends BaseAttributeDefinition {

    private final String nativeType;
    private final AttributeResolver attributeResolver;
    private final ScimAttributeMapping scim;

    public MappedAttribute(MappedAttributeBuilderImpl builder) {
        super(builder);
        this.nativeType = builder.nativeType;
        this.scim = builder.scim != null ? (ScimAttributeMapping) builder.scim.build() : null;
        builder.deffered.set(this);
        this.attributeResolver = builder.resolverBuilder != null ? builder.resolverBuilder.build() : null;
    }

    @Override
    public String nativeType() {
        return nativeType;
    }

    /**
     * Not routed through {@link #mapping(Class)} - the backing {@code protocolMappings} map on
     * {@code AbstractAttributeBuilder} is package-private in conndev.schema and unreachable from
     * here, so (mirroring connector-sql's own {@code .sql()}) the SCIM mapping is carried as its
     * own field instead.
     */
    public ScimAttributeMapping scim() {
        return scim;
    }

    public AttributeResolver attributeResolver() {
        return attributeResolver;
    }
}
