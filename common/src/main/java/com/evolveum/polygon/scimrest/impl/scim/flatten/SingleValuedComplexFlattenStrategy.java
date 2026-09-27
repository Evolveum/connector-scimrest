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
 * Flattens a single-valued complex attribute into plain attributes of the containing object
 * class: each scalar sub-attribute {@code <attribute>/<sub>} becomes an attribute named
 * {@code <attribute>_<sub>} whose SCIM path points into the complex attribute. Nested complex
 * sub-attributes are skipped.
 *
 * <p>Handles the well-known {@code name} attribute as well as any other single-valued complex
 * attribute requested through the object-class {@code scim { flatten ... }} configuration. When
 * flattening an extension attribute, the produced paths are additionally qualified with the
 * extension's schema URI ({@code pathPrefix}) and the flat names with the extension alias
 * ({@code namePrefix}).
 */
public class SingleValuedComplexFlattenStrategy implements ComplexFlattenStrategy {

    private final String attribute;
    private final List<AttributePath.Component> pathPrefix;
    private final String namePrefix;

    public SingleValuedComplexFlattenStrategy(String attribute) {
        this(attribute, List.of(), "");
    }

    public SingleValuedComplexFlattenStrategy(String attribute,
                                              List<AttributePath.Component> pathPrefix,
                                              String namePrefix) {
        this.attribute = attribute;
        this.pathPrefix = List.copyOf(pathPrefix);
        this.namePrefix = namePrefix;
    }

    @Override
    public boolean supports(AttributeDefinition scimAttr) {
        return attribute.equals(scimAttr.getName())
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
            var path = new ArrayList<AttributePath.Component>(pathPrefix);
            path.add(new AttributePath.Attribute(attribute));
            path.add(new AttributePath.Attribute(subAttr.getName()));
            result.add(new FlattenedAttribute(namePrefix + attribute + "_" + subAttr.getName(),
                    new AttributePath(List.copyOf(path))));
        }
        return result;
    }
}
