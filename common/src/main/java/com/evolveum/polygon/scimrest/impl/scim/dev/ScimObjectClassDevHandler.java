/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.scim.dev;

import com.evolveum.polygon.conndev.dev.ConnDevObjectClassSerializer;
import com.evolveum.polygon.scimrest.ContextLookup;
import com.evolveum.polygon.scimrest.groovy.ConnectorContext;
import com.evolveum.polygon.scimrest.spi.ExecuteQueryProcessor;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.Filter;

/**
 * Search handler for the {@code conndev_ObjectClass} object class. Serializes the framework schema
 * model ({@code RestSchema}, populated from SCIM discovery by {@code ScimSchemaTranslator}) via the
 * shared {@link ConnDevObjectClassSerializer}, so the export is derived from the same single model as
 * the ConnId schema. Supports equals filters on {@code __UID__} / {@code __NAME__}.
 */
public class ScimObjectClassDevHandler implements ExecuteQueryProcessor {

    @Override
    public void executeQuery(ContextLookup contextLookup, Filter filter, ResultsHandler resultsHandler,
            OperationOptions operationOptions) {
        var schema = contextLookup.get(ConnectorContext.class).schema();
        if (schema == null) {
            return;
        }
        for (var object : ConnDevObjectClassSerializer.serializeAll(schema.objectClasses())) {
            if (!matchesFilter(filter, object)) {
                continue;
            }
            if (!resultsHandler.handle(object)) {
                return;
            }
        }
    }

    private boolean matchesFilter(Filter filter, ConnectorObject object) {
        if (filter == null) {
            return true;
        }
        if (filter instanceof EqualsFilter equalsFilter) {
            var attributeName = equalsFilter.getAttribute().getName();
            var filterValue = AttributeUtil.getSingleValue(equalsFilter.getAttribute());
            if (Uid.NAME.equals(attributeName)) {
                return object.getUid().getUidValue().equals(filterValue);
            }
            if (Name.NAME.equals(attributeName)) {
                return object.getName().getNameValue().equals(filterValue);
            }
        }
        return false;
    }
}
