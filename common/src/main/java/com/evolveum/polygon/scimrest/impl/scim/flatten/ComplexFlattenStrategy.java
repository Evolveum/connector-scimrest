/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.scim.flatten;

import com.evolveum.polygon.conndev.api.AttributePath;
import com.unboundid.scim2.common.types.AttributeDefinition;

import java.util.List;

/**
 * A SCIM mapping rule that flattens a complex attribute into plain attributes of the containing
 * object class instead of an embedded object class.
 *
 * <p>A strategy decides which complex attributes it handles ({@link #supports}) and how each is
 * decomposed into flat attributes ({@link #flatten}). The translator applies the produced
 * {@link FlattenedAttribute}s by creating the attribute and wiring its SCIM path; attribute
 * metadata (type, mutability, ...) is then applied by the regular attribute mapping rules.
 */
public interface ComplexFlattenStrategy {

    /** Whether this strategy flattens the given SCIM complex attribute. */
    boolean supports(AttributeDefinition scimAttr);

    /** The flat attributes this strategy produces for the given complex attribute. */
    List<FlattenedAttribute> flatten(AttributeDefinition scimAttr);

    /**
     * A plain attribute to add to the containing object class: its ConnId/protocol name and the
     * SCIM path it maps to.
     */
    record FlattenedAttribute(String name, AttributePath path) {
    }
}
