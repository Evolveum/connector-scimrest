/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy.api;

import com.evolveum.polygon.scimrest.schema.MappedObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.filter.Filter;

/**
 * Context for search script execution. Provides access to search parameters and utilities
 * for creating filters and accessing other object classes.
 */
public interface SearchScriptContext extends BaseScriptContext {

    /**
     * Returns the object class definition for the current search operation.
     * 
     * @return the object class definition
     */
    MappedObjectClass definition();

    /**
     * Returns the result handler for the current search operation.
     * The result handler is used to report found objects.
     * 
     * @return the result handler
     */
    ResultsHandler resultHandler();

    /**
     * Returns the filter for the current search operation.
     * 
     * @return the filter
     */
    Filter filter();

    /**
     * Returns the operation options for the current search operation.
     * 
     * @return the operation options
     */
    OperationOptions operationOptions();


}
