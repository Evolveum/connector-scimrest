package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.spi.ExecuteQueryProcessor;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.filter.Filter;

public interface FilterAwareExecuteQueryProcessor<HC> extends ExecuteQueryProcessor<HC> {

    default boolean supports(Filter filter, OperationOptions operationOptions) {
        return supports(filter);
    }

    boolean supports(Filter filter);


}
