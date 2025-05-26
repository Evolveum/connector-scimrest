package com.evolveum.polygon.scim.rest.groovy.api;

import com.evolveum.polygon.scim.rest.schema.RestObjectClass;
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
    RestObjectClass definition();

    /**
     * Creates a filter builder for the specified attribute using its protocol name.
     * This method is used to create filters for attributes in custom search implementations.
     * 
     * @param protocolName the protocol name of the attribute to filter on
     * @return a filter builder for the specified attribute
     * @throws IllegalArgumentException if the attribute is not found
     */
    default FilterBuilder attributeFilter(String protocolName) {
        var attribute = definition().attributeFromProtocolName(protocolName);
        if (attribute == null) {
            throw new IllegalArgumentException("Unknown attribute: " + protocolName);
        }

        return FilterBuilder.forAttribute(attribute.connId().getName());
    }

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
