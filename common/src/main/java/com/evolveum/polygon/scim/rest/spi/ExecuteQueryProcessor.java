package com.evolveum.polygon.scim.rest.spi;

import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.filter.Filter;

@FunctionalInterface
public interface ExecuteQueryProcessor<HC> extends ObjectClassOperation<HC> {

    void executeQuery(HC context, Filter filter, ResultsHandler resultsHandler, OperationOptions operationOptions);

}
