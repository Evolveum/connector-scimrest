package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.groovy.api.ObjectClassScripting;
import com.evolveum.polygon.scim.rest.groovy.api.SearchScriptContext;
import com.evolveum.polygon.scim.rest.schema.MappedObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.filter.Filter;

public record SearchScriptContextImpl(ConnectorContext context, MappedObjectClass definition, Filter filter,
                                      ResultsHandler resultHandler, OperationOptions operationOptions) implements SearchScriptContext {

    @Override
    public MappedObjectClass definition() {
        return definition;
    }

    @Override
    public ObjectClassScripting objectClass(String name) {
        return ObjectClassScriptingFacade.from(context, name);
    }


}
