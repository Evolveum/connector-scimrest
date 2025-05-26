package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.groovy.api.ObjectClassScripting;
import com.evolveum.polygon.scim.rest.groovy.api.SearchScriptContext;
import com.evolveum.polygon.scim.rest.schema.RestObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.filter.Filter;

public record SearchScriptContextImpl(ConnectorContext context, RestObjectClass definition, Filter filter,
                                      ResultsHandler resultHandler, OperationOptions operationOptions) implements SearchScriptContext {

    @Override
    public RestObjectClass definition() {
        return definition;
    }

    @Override
    public ObjectClassScripting objectClass(String name) {
        var schema = context.schema().objectClass(name);
        if (schema == null) {
            throw new IllegalArgumentException("No such object class: " + name);
        }
        var handler = context.handlerFor(schema.objectClass());
        return new ObjectClassScriptingFacade(context.rest(), schema, handler);
    }


}
