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
import java.util.Set;

/**
 * Flattens a multi-valued complex attribute into one plain attribute per entry {@code type},
 * keyed by the entry's {@code type} discriminator and mapped to
 * {@code <family>[type eq "<type>"].<sub>}.
 *
 * <p>For the {@code value} sub-attribute the flat name is {@code <type>_<singular>}
 * (e.g. {@code work_email}); for every other sub-attribute it is
 * {@code <type>_<singular>_<sub>} (e.g. {@code work_address_locality}).
 *
 * <p>{@code includedSubAttributes} restricts which sub-attributes are flattened: a {@code null}
 * value means "every scalar sub-attribute except {@code type}" (used for {@code addresses}), while
 * an explicit set (e.g. {@code {value}}) is used for {@code emails}/{@code phoneNumbers}.
 */
public class TypeBasedComplexFlattenStrategy implements ComplexFlattenStrategy {

    private static final String TYPE_DISCRIMINATOR = "type";
    private static final String VALUE_SUB_ATTRIBUTE = "value";

    private final String family;
    private final String singular;
    private final List<String> typeSet;
    private final Set<String> includedSubAttributes;

    public TypeBasedComplexFlattenStrategy(String family, String singular,
                                           List<String> typeSet, Set<String> includedSubAttributes) {
        this.family = family;
        this.singular = singular;
        this.typeSet = List.copyOf(typeSet);
        this.includedSubAttributes = includedSubAttributes;
    }

    @Override
    public boolean supports(AttributeDefinition scimAttr) {
        return family.equals(scimAttr.getName())
                && AttributeDefinition.Type.COMPLEX.equals(scimAttr.getType())
                && scimAttr.isMultiValued();
    }

    @Override
    public List<FlattenedAttribute> flatten(AttributeDefinition scimAttr) {
        var result = new ArrayList<FlattenedAttribute>();
        for (String type : typeSet) {
            for (String sub : candidateSubAttributes(scimAttr)) {
                var name = VALUE_SUB_ATTRIBUTE.equals(sub)
                        ? type + "_" + singular
                        : type + "_" + singular + "_" + sub;
                var path = AttributePath.of(family).valueFilter(TYPE_DISCRIMINATOR, type).child(sub);
                result.add(new FlattenedAttribute(name, path));
            }
        }
        return result;
    }

    private List<String> candidateSubAttributes(AttributeDefinition scimAttr) {
        var result = new ArrayList<String>();
        var subAttributes = Objects.requireNonNullElse(scimAttr.getSubAttributes(), List.<AttributeDefinition>of());
        for (var subAttr : subAttributes) {
            if (AttributeDefinition.Type.COMPLEX.equals(subAttr.getType())) {
                continue;
            }
            if (TYPE_DISCRIMINATOR.equals(subAttr.getName())) {
                // The discriminator is already encoded in the flat attribute name.
                continue;
            }
            if (includedSubAttributes == null || includedSubAttributes.contains(subAttr.getName())) {
                result.add(subAttr.getName());
            }
        }
        return result;
    }
}
