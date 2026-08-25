/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.scim.flatten;

import com.evolveum.polygon.conndev.api.AttributePath;
import com.unboundid.scim2.common.types.AttributeDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Flattens the single-valued {@code name} complex attribute into plain attributes of the
 * containing object class: each scalar sub-attribute {@code name/<sub>} becomes an attribute
 * named {@code name_<sub>} whose SCIM path points into the complex attribute. Nested complex
 * sub-attributes are skipped.
 */
public class NameComplexFlattenStrategy implements ComplexFlattenStrategy {

    private static final String NAME = "name";

    @Override
    public boolean supports(AttributeDefinition scimAttr) {
        return NAME.equals(scimAttr.getName())
                && AttributeDefinition.Type.COMPLEX.equals(scimAttr.getType())
                && !scimAttr.isMultiValued();
    }

    @Override
    public List<FlattenedAttribute> flatten(AttributeDefinition scimAttr) {
        var result = new ArrayList<FlattenedAttribute>();
        var subAttributes = Objects.requireNonNullElse(scimAttr.getSubAttributes(), List.<AttributeDefinition>of());
        for (var subAttr : subAttributes) {
            if (AttributeDefinition.Type.COMPLEX.equals(subAttr.getType())) {
                continue;
            }
            result.add(new FlattenedAttribute(NAME + "_" + subAttr.getName(),
                    AttributePath.of(NAME, subAttr.getName())));
        }
        return result;
    }
}
