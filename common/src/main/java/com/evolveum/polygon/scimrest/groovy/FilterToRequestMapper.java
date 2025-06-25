/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy;

import com.evolveum.polygon.scimrest.impl.rest.RestContext;
import com.evolveum.polygon.scimrest.groovy.api.FilterSpecification;
import org.identityconnectors.framework.common.objects.filter.Filter;

import java.util.function.BiConsumer;

public interface FilterToRequestMapper {

    FilterSpecification filterSpecification();

    default boolean canHandle(Filter filter) {
        return filterSpecification().matches(filter);
    }

    void mapToRequest(RestContext.RequestBuilder builder, Filter filter);

    static FilterToRequestMapper from(FilterSpecification specification, BiConsumer<RestContext.RequestBuilder, Filter> mapper) {
        return new FilterToRequestMapper() {
            @Override
            public FilterSpecification filterSpecification() {
                return specification;
            }

            @Override
            public void mapToRequest(RestContext.RequestBuilder builder, Filter filter) {
                mapper.accept(builder, filter);
            }
        };
    }
}
