/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.scim.flatten;

import com.evolveum.polygon.scimrest.config.ScimClientConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Builds the enabled {@link ComplexFlattenStrategy}s for a SCIM mapping configuration from the
 * per-family {@code SCIM Mapping} flags.
 */
public final class ScimFlattenStrategies {

    /** Entry types used when flattening multi-valued complex attributes by {@code type}. */
    public static final List<String> DEFAULT_TYPE_SET = List.of("work", "home", "other");

    private static final Set<String> ADDRESS_SUB_ATTRIBUTES =
            Set.of("formatted", "streetAddress", "locality", "region", "postalCode", "country");

    private ScimFlattenStrategies() {
    }

    /** The flatten strategies enabled by the given configuration, in application order. */
    public static List<ComplexFlattenStrategy> forConfiguration(ScimClientConfiguration configuration) {
        var strategies = new ArrayList<ComplexFlattenStrategy>();
        if (Boolean.TRUE.equals(configuration.getScimFlattenNameAttribute())) {
            strategies.add(new NameComplexFlattenStrategy());
        }
        if (Boolean.TRUE.equals(configuration.getScimFlattenEmails())) {
            strategies.add(new TypeBasedComplexFlattenStrategy(
                    "emails", "email", DEFAULT_TYPE_SET, Set.of("value")));
        }
        if (Boolean.TRUE.equals(configuration.getScimFlattenPhoneNumbers())) {
            strategies.add(new TypeBasedComplexFlattenStrategy(
                    "phoneNumbers", "phone", DEFAULT_TYPE_SET, Set.of("value")));
        }
        if (Boolean.TRUE.equals(configuration.getScimFlattenAddresses())) {
            strategies.add(new TypeBasedComplexFlattenStrategy(
                    "addresses", "address", DEFAULT_TYPE_SET, ADDRESS_SUB_ATTRIBUTES));
        }
        return List.copyOf(strategies);
    }
}
