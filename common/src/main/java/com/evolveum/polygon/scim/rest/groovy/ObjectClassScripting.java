package com.evolveum.polygon.scim.rest.groovy;

import org.identityconnectors.framework.common.objects.ConnectorObject;
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
    void search(Filter filter, ResultsHandler consumer);
}
