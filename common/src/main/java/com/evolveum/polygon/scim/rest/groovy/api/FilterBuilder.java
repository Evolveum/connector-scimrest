package com.evolveum.polygon.scim.rest.groovy.api;

import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.Filter;

public interface FilterBuilder {


    static AttributeFilterBuilder forAttribute(String name) {
        return new AttributeFilterBuilder(name);
    }

    record AttributeFilterBuilder(String name) implements FilterBuilder {

        public EqualsFilter eq(Object value) {
            return new EqualsFilter(AttributeBuilder.build(name, value));
        }

        @Override
        public Filter build() {
            return null;
        }
    }

    Filter build();
}
