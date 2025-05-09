package com.evolveum.polygon.scim.rest.spi;

import com.evolveum.polygon.scim.rest.groovy.RestSearchOperationHandler;
import org.identityconnectors.framework.common.objects.filter.Filter;

public interface SearchEndpointHandler<BF, OF> {


    RestSearchOperationHandler process(Filter filter);
}
