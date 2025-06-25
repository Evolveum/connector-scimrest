/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.spi;

import com.evolveum.polygon.scimrest.ContextLookup;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.filter.Filter;

@FunctionalInterface
public interface ExecuteQueryProcessor extends ObjectClassOperation {

    void executeQuery(ContextLookup context, Filter filter, ResultsHandler resultsHandler, OperationOptions operationOptions);

}
