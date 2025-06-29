/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy;

import com.evolveum.polygon.scimrest.ContextLookup;
import com.evolveum.polygon.scimrest.ObjectClassHandler;
import com.evolveum.polygon.scimrest.groovy.api.ObjectClassScripting;
import com.evolveum.polygon.scimrest.schema.MappedObjectClass;
import com.evolveum.polygon.scimrest.spi.ExecuteQueryProcessor;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.filter.Filter;


public record ObjectClassScriptingFacade(ContextLookup rest, MappedObjectClass schema, ObjectClassHandler handler) implements ObjectClassScripting {

    static ObjectClassScriptingFacade from(ConnectorContext context, String objectClass) {
        var schema = context.schema().objectClass(objectClass);
        if (schema == null) {
            throw new IllegalArgumentException("No such object class: " + objectClass);
        }
        var handler = context.handlerFor(schema.objectClass());
        return new ObjectClassScriptingFacade(context, schema, handler);
    }

    @Override
    public MappedObjectClass definition() {
        return schema;
    }


    public void search(Filter filter, ResultsHandler consumer, OperationOptions options) {
        handler.checkSupported(ExecuteQueryProcessor.class).executeQuery(rest, filter, consumer, options);
    }
}
