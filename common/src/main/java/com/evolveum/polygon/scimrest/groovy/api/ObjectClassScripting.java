/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy.api;

import com.evolveum.polygon.scimrest.schema.MappedObjectClass;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.filter.Filter;

import java.util.ArrayList;

/**
 * Provides an access to existing implementation of the Object Class support.
 * This interface allows scripts to perform searches for objects of a specific class.
 */
public interface ObjectClassScripting {

    /**
     * Searches for all objects of this class without any specific filter.
     * 
     * @return a collection of objects
     */
    default Iterable<ConnectorObject> search() {
        return search(null);
    }

    /**
     * Searches for objects of this class which matches specified filter.
     * 
     * @param filter the filter to apply to the search
     * @return a collection of objects which matches the filter
     */
    default Iterable<ConnectorObject> search(Filter filter) {
        var ret = new ArrayList<ConnectorObject>();
        search(filter, ret::add);
        return ret;
    };

    /**
     * Searches for objects of this class using a FilterBuilder.
     * 
     * @param filter the filter builder to create a filter
     * @param consumer the result handler to process the results
     */
    default void search(FilterBuilder filter, ResultsHandler consumer) {
        search(filter.build(), consumer);
    }

    /**
     * Searches for objects of this class and reports the results to the specified result handler.
     * 
     * @param filter the filter to apply to the search
     * @param consumer the result handler to process the results
     */
    default void search(Filter filter, ResultsHandler consumer) {
        search(filter, consumer, null);
    }

    void search(Filter filter, ResultsHandler consumer, OperationOptions options);

    /**
     * Creates a filter builder for the specified attribute using its protocol name.
     * This method is used to create filters for attributes in custom search implementations.
     *
     * @param protocolName the protocol name of the attribute to filter on
     * @return a filter builder for the specified attribute
     * @throws IllegalArgumentException if the attribute is not found
     */
    default FilterBuilder.AttributeFilterBuilder attributeFilter(String protocolName) {
        var attribute = definition().attributeFromProtocolName(protocolName);
        if (attribute == null) {
            throw new IllegalArgumentException("Unknown attribute: " + protocolName);
        }

        return FilterBuilder.forAttribute(attribute.connId().getName());
    }

    MappedObjectClass definition();

}
