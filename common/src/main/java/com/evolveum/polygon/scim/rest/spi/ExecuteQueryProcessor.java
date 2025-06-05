package com.evolveum.polygon.scim.rest.spi;

import com.evolveum.polygon.scim.rest.ContextLookup;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.filter.Filter;

@FunctionalInterface
public interface ExecuteQueryProcessor extends ObjectClassOperation {

    void executeQuery(ContextLookup context, Filter filter, ResultsHandler resultsHandler, OperationOptions operationOptions);

}
