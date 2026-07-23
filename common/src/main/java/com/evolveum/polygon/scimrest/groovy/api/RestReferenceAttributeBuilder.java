/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy.api;

import com.evolveum.polygon.conndev.build.api.ReferenceAttributeBuilder;
import com.evolveum.polygon.scimrest.schema.MappedAttribute;

public interface RestReferenceAttributeBuilder extends RestAttributeBuilder<RestReferenceAttributeBuilder>,
        ReferenceAttributeBuilder<RestReferenceAttributeBuilder, RestAttributeBuilder<RestReferenceAttributeBuilder>, MappedAttribute> {
}
