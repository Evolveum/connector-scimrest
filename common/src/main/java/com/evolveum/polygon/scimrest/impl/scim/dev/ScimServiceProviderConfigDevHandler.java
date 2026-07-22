/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.scim.dev;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.scimrest.groovy.ConnectorContext;
import com.evolveum.polygon.scimrest.impl.scim.ScimContext;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.Filter;

import com.evolveum.polygon.scimrest.spi.ExecuteQueryProcessor;

/**
 * Search handler for conndev_ScimServiceProviderConfig object class.
 * Returns the single discovered /ServiceProviderConfig document with its full JSON content,
 * or nothing when the service provider does not implement the endpoint.
 * Only supports id and name filters (equals).
 */
public class ScimServiceProviderConfigDevHandler implements ExecuteQueryProcessor {

    static final String OBJECT_NAME = "ServiceProviderConfig";

    private final ScimContext context;

    public ScimServiceProviderConfigDevHandler(ScimContext context) {
        this.context = context;
    }

    @Override
    public void executeQuery(ContextLookup contextLookup, Filter filter, ResultsHandler resultsHandler, OperationOptions operationOptions) {
        var providerConfig = context.getServiceProviderConfig();
        if (providerConfig == null || !matchesFilter(filter)) {
            return;
        }
        var connectorContext = contextLookup.get(ConnectorContext.class);
        var objectClass = connectorContext.schema().objectClass(ScimDevelopmentMode.SERVICE_PROVIDER_CONFIG_OC_NAME);

        ConnectorObjectBuilder builder = objectClass.newObjectBuilder();
        builder.addAttribute(Uid.NAME, OBJECT_NAME);
        builder.addAttribute(Name.NAME, OBJECT_NAME);
        builder.addAttribute("content", ScimDevelopmentMode.schemaToJson(providerConfig));
        resultsHandler.handle(builder.build());
    }

    private boolean matchesFilter(Filter filter) {
        if (filter == null) {
            return true;
        }

        if (filter instanceof EqualsFilter equalsFilter) {
            String attrName = equalsFilter.getAttribute().getName();
            Object filterValue = AttributeUtil.getSingleValue(equalsFilter.getAttribute());

            if (Uid.NAME.equals(attrName) || Name.NAME.equals(attrName)) {
                return OBJECT_NAME.equals(filterValue);
            }
        }

        return false;
    }
}
