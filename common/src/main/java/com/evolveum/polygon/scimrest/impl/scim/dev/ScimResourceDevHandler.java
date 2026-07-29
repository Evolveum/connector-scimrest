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
import com.evolveum.polygon.scimrest.impl.scim.ScimResourceContext;
import com.evolveum.polygon.scimrest.spi.ExecuteQueryProcessor;
import com.unboundid.scim2.common.types.ResourceTypeResource;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Search handler for conndev_ScimResource object class.
 * Returns all discovered SCIM resource definitions with their schema details.
 * Only supports id, name, and schema filters (equals).
 */
public class ScimResourceDevHandler implements ExecuteQueryProcessor {

    private final ScimContext context;

    public ScimResourceDevHandler(ScimContext context) {
        this.context = context;
    }

    @Override
    public void executeQuery(ContextLookup contextLookup, Filter filter, ResultsHandler resultsHandler, OperationOptions operationOptions) {
        var resources = context.getResources();
        var connectorContext = contextLookup.get(com.evolveum.polygon.scimrest.groovy.ConnectorContext.class);
        var schema = connectorContext.schema();
        var objectClass = schema.objectClass(ScimDevelopmentMode.RESOURCE_OC_NAME);
        
        for (Map.Entry<String, ScimResourceContext> entry : resources.entrySet()) {
            String resourceId = entry.getKey();
            ScimResourceContext resourceContext = entry.getValue();
            var resource = resourceContext.resource();
            
            if (!matchesFilter(filter, resourceId, resource.getName(), resourceContext.primarySchema())) {
                continue;
            }
            
            ConnectorObject obj = createResourceObject(objectClass, resourceId, resource, resourceContext);
            if (!resultsHandler.handle(obj)) {
                return;
            }
        }
    }

    private boolean matchesFilter(Filter filter, String resourceId, String resourceName, SchemaResource primarySchema) {
        if (filter == null) {
            return true;
        }
        
        if (filter instanceof EqualsFilter equalsFilter) {
            String attrName = equalsFilter.getAttribute().getName();
            Object filterValue = AttributeUtil.getSingleValue(equalsFilter.getAttribute());
            
            if (Uid.NAME.equals(attrName)) {
                return resourceId.equals(filterValue);
            }
            if (Name.NAME.equals(attrName)) {
                return resourceName.equals(filterValue);
            }
            if ("schema".equals(attrName)) {
                String primarySchemaUri = primarySchema != null ? primarySchema.getId() : null;
                return primarySchemaUri != null && primarySchemaUri.equals(filterValue);
            }
        }
        
        return false;
    }

    private ConnectorObject createResourceObject(MappedObjectClass objectClass, String resourceId, ResourceTypeResource resource, ScimResourceContext resourceContext) {
        String primarySchemaJson = ScimDevelopmentMode.schemaToJson(resourceContext.primarySchema());
        String schemaExtensionsJson = schemaExtensionsToJson(resourceContext.extensions());
        
        ConnectorObjectBuilder builder = objectClass.newObjectBuilder();
        builder.addAttribute(Uid.NAME, resourceId);
        builder.addAttribute(Name.NAME, resource.getName());
        builder.addAttribute("schema", resource.getSchema() != null ? resource.getSchema().toString() : null);
        builder.addAttribute("endpoint", resourceContext.relativeEndpoint());
        builder.addAttribute("primarySchema", primarySchemaJson);
        builder.addAttribute("schemaExtensions", schemaExtensionsJson);
        return builder.build();
    }

    private String schemaExtensionsToJson(Map<String, SchemaResource> extensions) {
        if (extensions == null || extensions.isEmpty()) {
            return "[]";
        }
        List<String> extensionList = new ArrayList<>();
        for (SchemaResource ext : extensions.values()) {
            extensionList.add(ScimDevelopmentMode.schemaToJson(ext));
        }
        return "[" + String.join(",", extensionList) + "]";
    }
}
