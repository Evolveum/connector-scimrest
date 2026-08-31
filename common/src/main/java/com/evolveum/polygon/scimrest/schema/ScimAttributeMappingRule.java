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
import com.unboundid.scim2.common.types.AttributeDefinition;

/**
 * Strategy for detecting properties from SCIM attribute metadata at the attribute level.
 * <p>
 * A thin binding of conndev's shared {@link MappingRule} to SCIM's concrete types — the context
 * is a {@link Context} (resource + attribute definition).
 */
public interface ScimAttributeMappingRule extends MappingRule<
        ScimAttributeMappingRule.Context,
        RestObjectClassSchemaBuilder,
        RestAttributeBuilder<RestReferenceAttributeBuilder>,
        BaseOperationSupportBuilder> {

    /**
     * The context an attribute-level SCIM rule needs: the resource it belongs to, and the
     * attribute definition it describes. Bundled into one record so this rule fits conndev's
     * shared {@code MappingRule<C, OC, A, H>} shape (a single context type), rather than carrying
     * two separate context parameters.
     */
    record Context(ScimResourceContext resource, AttributeDefinition attribute) {
    }
}
