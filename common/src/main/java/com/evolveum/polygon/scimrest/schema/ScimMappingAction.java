/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.schema;

import java.util.function.Consumer;

/**
 * Represents a detected property from SCIM schema metadata that affects
 * both schema definitions and handler configurations.
 * <p>
 * Actions are created by {@link ScimResourceMappingRule} and
 * {@link ScimAttributeMappingRule} implementations and applied during
 * schema translation.
 */
public interface ScimMappingAction {

    /**
     * Apply schema effects to the object class.
     *
     * @param objectClass the object class builder
     */
    default void applyToSchema(RestObjectClassDefinitionBuilder objectClass) {
    }

    /**
     * Apply handler effects.
     * Schema-only actions may leave this empty.
     */
    default void applyToHandlers() {
    }

    /**
     * Attribute-specific action that targets a particular attribute by SCIM name.
     */
    interface AttributeSpecific extends ScimMappingAction {

        /**
         * @return the SCIM attribute name this action targets
         */
        String attributeName();

        /**
         * Apply schema effects to the specified attribute.
         *
         * @param objectClass the object class builder
         * @param attribute   the attribute builder
         */
        void applyToAttribute(RestObjectClassDefinitionBuilder objectClass, RestAttributeBuilderImpl attribute);

        @Override
        default void applyToSchema(RestObjectClassDefinitionBuilder objectClass) {
            // Attribute-specific actions operate via applyToAttribute
        }
    }

    /**
     * Creates an attribute-specific action that applies a transformation to the attribute
     * builder associated with the specified SCIM attribute name.
     *
     * @param attrName    the SCIM attribute name
     * @param transform   the consumer that transforms the attribute builder
     * @return an attribute-specific action instance
     */
    static ScimMappingAction.AttributeSpecific attributeSpecific(String attrName,
                                                                  Consumer<RestAttributeBuilderImpl> transform) {
        return new AttrSpecificAction(attrName, transform);
    }

    record AttrSpecificAction(String attributeName, Consumer<RestAttributeBuilderImpl> transform)
            implements AttributeSpecific {

        @Override
        public void applyToAttribute(RestObjectClassDefinitionBuilder objectClass,
                                     RestAttributeBuilderImpl attribute) {
            transform.accept(attribute);
        }
    }
}
