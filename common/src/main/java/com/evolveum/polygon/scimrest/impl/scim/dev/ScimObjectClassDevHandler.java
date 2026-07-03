/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.scim.dev;

import com.evolveum.polygon.scimrest.ContextLookup;
import com.evolveum.polygon.scimrest.impl.scim.ScimContext;
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
 * Search handler for the {@code conndev_ObjectClass} object class. Links every discovered SCIM
 * resource type with its schemas (primary + extensions) via {@link ScimConnDevMapper} and returns
 * the structured representation. Supports equals filters on {@code __UID__} / {@code __NAME__}.
 */
public class ScimObjectClassDevHandler implements ExecuteQueryProcessor {

    private final ScimContext context;
    private final ScimConnDevMapper mapper = new ScimConnDevMapper();

    public ScimObjectClassDevHandler(ScimContext context) {
        this.context = context;
    }

    @Override
    public void executeQuery(ContextLookup contextLookup, Filter filter, ResultsHandler resultsHandler,
            OperationOptions operationOptions) {
        var schemasByUrn = context.getSchemas();
        for (var resourceContext : context.getResources().values()) {
            var object = mapper.toObjectClass(resourceContext.resource(), schemasByUrn);
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
