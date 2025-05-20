package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.spi.SearchEndpointHandler;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.Filter;

import java.util.HashSet;

public class MultipleEndpointSearchProcessor<HC>  implements FilterRequestProcessor {

    private final static FilterSpecification.Attribute IS_UID_FILTER_WITH_SINGLE_VALUE = FilterSpecification.attribute(Uid.NAME).eq().anySingleValue();

    private final SearchEndpointHandler<?,?> defaultHandler;
    private final HashSet<SearchEndpointHandler<?,?>> endpoints;

    public MultipleEndpointSearchProcessor(SearchEndpointHandler<?,?> defaultHandler, HashSet<SearchEndpointHandler<?,?>> endpoints) {
        this.defaultHandler = defaultHandler;
        this.endpoints = endpoints;
    }


    public RestSearchOperationHandler<?,?> createRequest(Filter filter) {
        if (isEmpty(filter)) {
            return defaultHandler.process(filter);
        }
        for (SearchEndpointHandler<?,?> handler : endpoints) {
            if (handler.process(filter) != null) {
                return handler.process(filter);
            }
        }
        if(IS_UID_FILTER_WITH_SINGLE_VALUE.matches(filter)) {
            // Let's try with name filter
            EqualsFilter eqFilter = (EqualsFilter) filter;
            if (eqFilter.getAttribute() instanceof Uid uid) {
                var nameHint = uid.getNameHint();
                if (nameHint != null) {
                    return createRequest(new EqualsFilter(nameHint));
                }
            }
        }

        throw new IllegalStateException("No endpoint found for filter " + filter);
    }

    boolean isEmpty(Filter filter) {
        return filter == null;
    }
}
