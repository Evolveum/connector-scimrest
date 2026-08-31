/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.schema;

import com.evolveum.polygon.conndev.concepts.MappingAction;
import com.evolveum.polygon.scimrest.groovy.api.RestAttributeBuilder;
import com.evolveum.polygon.scimrest.groovy.api.RestObjectClassSchemaBuilder;
import com.evolveum.polygon.scimrest.groovy.api.RestReferenceAttributeBuilder;
import com.evolveum.polygon.scimrest.groovy.schema.BaseOperationSupportBuilder;

/**
 * A thin binding of conndev's shared {@link MappingAction} to SCIM's concrete types — every
 * {@link ScimResourceMappingRule}/{@link ScimAttributeMappingRule} action returns this, since both
 * bind the same {@code OC}/{@code A}/{@code H} type arguments.
 */
public interface ScimMappingAction extends MappingAction<
        RestObjectClassSchemaBuilder, RestAttributeBuilder<RestReferenceAttributeBuilder>, BaseOperationSupportBuilder> {
}
