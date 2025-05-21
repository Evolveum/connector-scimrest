package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.RestContext;
import com.evolveum.polygon.scim.rest.RestJsonExecuteQueryProcessor;
import com.evolveum.polygon.scim.rest.schema.RestObjectClass;
import org.identityconnectors.framework.common.objects.filter.Filter;

// FIXME: Consider merging this with RestJsonExecuteQueryProcessor
public class RequestProcessorBasedSearch extends RestJsonExecuteQueryProcessor<RestContext> {

    private final FilterRequestProcessor processor;

    public RequestProcessorBasedSearch(RestObjectClass clazz, FilterRequestProcessor o) {
        super(clazz);
        this.processor = o;
    }

    public static RequestProcessorBasedSearch from(RestObjectClass clazz, FilterRequestProcessor o) {
        return new RequestProcessorBasedSearch(clazz, o);
    }

    @Override
    protected RestSearchOperationHandler createSearchSpecification(Filter query) {
        return processor.createRequest(query);
    }
}
