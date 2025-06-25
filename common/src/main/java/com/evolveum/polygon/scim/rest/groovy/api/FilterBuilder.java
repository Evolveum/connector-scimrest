/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scim.rest.groovy.api;

import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObjectReference;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.Filter;

public interface FilterBuilder {


    static AttributeFilterBuilder forAttribute(String name) {
        return new AttributeFilterBuilder(name);
    }

    record AttributeFilterBuilder(String name) implements FilterBuilder {

        public EqualsFilter eq(Object value) {
            if (value instanceof ConnectorObjectBuilder builder) {
                value = new ConnectorObjectReference(builder.build());
            }
            return new EqualsFilter(AttributeBuilder.build(name, value));
        }

        @Override
        public Filter build() {
            return null;
        }
    }

    Filter build();
}
