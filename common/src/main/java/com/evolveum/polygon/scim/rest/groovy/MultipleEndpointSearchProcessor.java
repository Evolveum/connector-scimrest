package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.spi.SearchEndpointHandler;
import org.identityconnectors.framework.common.objects.filter.Filter;

import java.util.HashSet;

public class MultipleEndpointSearchProcessor<HC>  implements FilterRequestProcessor {


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
        throw new IllegalStateException("No endpoint found for filter " + filter);
    }

    boolean isEmpty(Filter filter) {
        return filter == null;
    }
}
