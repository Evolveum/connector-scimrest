/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.schema;

import com.evolveum.polygon.conndev.concepts.MappingRule;
import com.evolveum.polygon.scimrest.groovy.api.RestAttributeBuilder;
import com.evolveum.polygon.scimrest.groovy.api.RestObjectClassSchemaBuilder;
import com.evolveum.polygon.scimrest.groovy.api.RestReferenceAttributeBuilder;
import com.evolveum.polygon.scimrest.groovy.schema.BaseOperationSupportBuilder;
import com.evolveum.polygon.scimrest.impl.scim.ScimResourceContext;

/**
 * Strategy for detecting properties from SCIM resource-type metadata at the resource level.
 * <p>
 * A thin binding of conndev's shared {@link MappingRule} to SCIM's concrete types — the context
 * is a {@link ScimResourceContext}.
 */
public interface ScimResourceMappingRule extends MappingRule<
        ScimResourceContext,
        RestObjectClassSchemaBuilder,
        RestAttributeBuilder<RestReferenceAttributeBuilder>,
        BaseOperationSupportBuilder> {
}
