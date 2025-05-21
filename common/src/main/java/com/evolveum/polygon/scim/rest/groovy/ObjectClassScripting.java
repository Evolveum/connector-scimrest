package com.evolveum.polygon.scim.rest.groovy;

import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.filter.Filter;

import java.util.ArrayList;

public interface ObjectClassScripting {

    default Iterable<ConnectorObject> search() {
        return search(null);
    }

    default Iterable<ConnectorObject> search(Filter filter) {
        var ret = new ArrayList<ConnectorObject>();
        search(filter, ret::add);
        return ret;
    };

    default void search(FilterBuilder filter, ResultsHandler consumer) {
        search(filter.build(), consumer);
    }

    void search(Filter filter, ResultsHandler consumer);
}
