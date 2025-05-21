package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.schema.RestObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.filter.Filter;

public interface SearchScriptContext {

    RestObjectClass definition();

    default FilterBuilder attributeFilter(String protocolName) {
        var attribute = definition().attributeFromProtocolName(protocolName);
        if (attribute == null) {
            throw new IllegalArgumentException("Unknown attribute: " + protocolName);
        }

        return FilterBuilder.forAttribute(attribute.connId().getName());
    }

    ResultsHandler resultHandler();

    Filter filter();

    OperationOptions operationOptions();

    ObjectClassScripting objectClass(String name);

}
