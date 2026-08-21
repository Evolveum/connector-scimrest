/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.schema;

import com.evolveum.polygon.scimrest.impl.scim.ScimResourceContext;

/**
 * Strategy for detecting properties from SCIM resource-type metadata at the resource level.
 * <p>
 * Rules are evaluated against each SCIM resource (object class).
 * Applicable rules produce {@link ScimMappingAction} instances that
 * modify both schema definitions and handler configurations.
 * <p>
 * Usage follows a two-phase pattern:
 * <ol>
 *   <li>{@code checkIfApplicable(resource)} determines if the rule applies</li>
 *   <li>{@code createAction(resource)} produces an action to apply</li>
 * </ol>
 */
public interface ScimResourceMappingRule {

    /**
     * Check if this rule is applicable to the given SCIM resource.
     *
     * @param resource the SCIM resource context
     * @return {@code true} if this rule has effects
     */
    boolean checkIfApplicable(ScimResourceContext resource);

    /**
     * Create a mapping action.
     * Called only when {@link #checkIfApplicable(ScimResourceContext)} returns {@code true}.
     *
     * @param resource the SCIM resource context
     * @return an action to apply, or {@code null} if nothing to apply
     */
    default ScimMappingAction createAction(ScimResourceContext resource) {
        return null;
    }
}
