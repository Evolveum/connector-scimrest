/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy;

import com.evolveum.polygon.scimrest.groovy.api.ObjectClassScripting;
import com.evolveum.polygon.scimrest.groovy.api.SearchScriptContext;
import com.evolveum.polygon.scimrest.schema.MappedObjectClass;
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
