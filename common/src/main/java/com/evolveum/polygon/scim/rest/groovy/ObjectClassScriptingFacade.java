package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.ObjectClassHandler;
import com.evolveum.polygon.scim.rest.RestContext;
import com.evolveum.polygon.scim.rest.schema.RestObjectClass;
import com.evolveum.polygon.scim.rest.spi.ExecuteQueryProcessor;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.filter.Filter;


public record ObjectClassScriptingFacade(RestContext rest, RestObjectClass schema, ObjectClassHandler<RestContext> handler) implements ObjectClassScripting {


    @Override
    public void search(Filter filter, ResultsHandler consumer) {
        handler.checkSupported(ExecuteQueryProcessor.class).executeQuery(rest, filter, consumer, null);
    }
}
