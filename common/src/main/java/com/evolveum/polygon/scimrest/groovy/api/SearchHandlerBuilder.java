package com.evolveum.polygon.scimrest.groovy.api;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public interface SearchHandlerBuilder<R extends SearchHandlerBuilder<R>> {

    /**
     * Sets whether the search endpoint supports filtering with empty filter criteria.
     *
     * Only one such endpoint / custom script may be defined for whole search handler.
     *
     * @param emptyFilterSupported true if the endpoint should be used for searches without filters.
     */
    RestSearchEndpointBuilder emptyFilterSupported(boolean emptyFilterSupported);

    FilterSpecification.Attribute attribute(String name);

    RestSearchEndpointBuilder supportedFilter(FilterSpecification filterSpec);

    RestSearchEndpointBuilder supportedFilter(FilterSpecification filterSpec, @DelegatesTo(value = FilterSupportImplementation.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure);

    interface FilterSupportImplementation {

    }


}
