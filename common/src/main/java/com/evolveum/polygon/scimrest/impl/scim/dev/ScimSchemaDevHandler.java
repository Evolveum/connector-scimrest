/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.scim.dev;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.scimrest.impl.scim.ScimContext;
import com.evolveum.polygon.scimrest.schema.MappedObjectClass;
import com.evolveum.polygon.scimrest.spi.ExecuteQueryProcessor;
import com.unboundid.scim2.common.types.SchemaResource;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.Filter;

import java.util.Map;

/**
 * Search handler for conndev_ScimSchema object class.
 * Returns all discovered SCIM schemas with their full JSON content.
 * Only supports id and name filters (equals).
 */
public class ScimSchemaDevHandler implements ExecuteQueryProcessor {


    private final ScimContext context;

    public ScimSchemaDevHandler(ScimContext context) {
        this.context = context;
    }

    @Override
    public void executeQuery(ContextLookup contextLookup, Filter filter, ResultsHandler resultsHandler, OperationOptions operationOptions) {
        var schemas = context.getSchemas();
        var connectorContext = contextLookup.get(com.evolveum.polygon.scimrest.groovy.ConnectorContext.class);
        var schema = connectorContext.schema();
        var objectClass = schema.objectClass(ScimDevelopmentMode.SCHEMA_OC_NAME);
        
        for (Map.Entry<String, SchemaResource> entry : schemas.entrySet()) {
            String schemaId = entry.getKey();
            SchemaResource schemaResource = entry.getValue();
            
            if (!matchesFilter(filter, schemaId, schemaResource.getName())) {
                continue;
            }
            
            ConnectorObject obj = createSchemaObject(objectClass, schemaId, schemaResource);
            if (!resultsHandler.handle(obj)) {
                return;
            }
        }
    }

    private boolean matchesFilter(Filter filter, String schemaId, String schemaName) {
        if (filter == null) {
            return true;
        }
        
        if (filter instanceof EqualsFilter equalsFilter) {
            String attrName = equalsFilter.getAttribute().getName();
            Object filterValue = AttributeUtil.getSingleValue(equalsFilter.getAttribute());
            
            if (Uid.NAME.equals(attrName)) {
                return schemaId.equals(filterValue);
            }
            if (Name.NAME.equals(attrName)) {
                return schemaName.equals(filterValue);
            }
        }
        
        return false;
    }

    private ConnectorObject createSchemaObject(MappedObjectClass objectClass, String schemaId, SchemaResource schemaResource) {
        String schemaContent = ScimDevelopmentMode.schemaToJson(schemaResource);
        
        ConnectorObjectBuilder builder = objectClass.newObjectBuilder();
        builder.addAttribute(Uid.NAME, schemaId);
        builder.addAttribute(Name.NAME, schemaResource.getName());
        builder.addAttribute("schemaContent", schemaContent);
        return builder.build();
    }
}
