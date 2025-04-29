package com.evolveum.polygon.scim.rest.groovy;

import org.identityconnectors.framework.common.objects.filter.Filter;

public interface FilterRequestProcessor {

    SearchHandler createRequest(Filter filter);

}
