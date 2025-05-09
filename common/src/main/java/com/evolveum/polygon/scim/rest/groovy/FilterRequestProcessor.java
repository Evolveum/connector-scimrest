package com.evolveum.polygon.scim.rest.groovy;

import org.identityconnectors.framework.common.objects.filter.Filter;

public interface FilterRequestProcessor {

    RestSearchOperationHandler<?,?> createRequest(Filter filter);

}
