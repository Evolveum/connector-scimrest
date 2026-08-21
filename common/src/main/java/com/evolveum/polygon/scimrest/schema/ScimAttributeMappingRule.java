/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.schema;

import com.evolveum.polygon.scimrest.impl.scim.ScimResourceContext;
import com.unboundid.scim2.common.types.AttributeDefinition;

/**
 * Strategy for detecting properties from SCIM attribute metadata at the attribute level.
 * <p>
 * Rules are evaluated against each attribute of a given SCIM resource.
 * Applicable rules produce {@link ScimMappingAction} instances that
 * modify both schema definitions and handler configurations.
 * <p>
 * Usage follows a two-phase pattern:
 * <ol>
 *   <li>{@code checkIfApplicable(resource, attr)} determines if the rule applies</li>
 *   <li>{@code createAction(resource, attr)} produces an action to apply</li>
 * </ol>
 */
public interface ScimAttributeMappingRule {

    /**
     * Check if this rule is applicable to the given SCIM resource and attribute.
     *
     * @param resource the SCIM resource context
     * @param attrDef  the SCIM attribute definition
     * @return {@code true} if this rule has effects for this resource/attribute
     */
    boolean checkIfApplicable(ScimResourceContext resource, AttributeDefinition attrDef);

    /**
     * Create a mapping action.
     * Called only when {@link #checkIfApplicable(ScimResourceContext, AttributeDefinition)}
     * returns {@code true}.
     *
     * @param resource the SCIM resource context
     * @param attrDef  the SCIM attribute definition
     * @return an action to apply, or {@code null} if nothing to apply
     */
    default ScimMappingAction createAction(ScimResourceContext resource, AttributeDefinition attrDef) {
        return null;
    }
}
