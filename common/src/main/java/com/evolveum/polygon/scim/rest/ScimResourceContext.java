package com.evolveum.polygon.scim.rest;

import com.unboundid.scim2.common.types.ResourceTypeResource;

import java.net.URI;
import java.util.HashMap;

public record ScimResourceContext(ResourceTypeResource resource, com.unboundid.scim2.common.types.SchemaResource primarySchema,
                                  HashMap<String, com.unboundid.scim2.common.types.SchemaResource> extensions)  {

    public URI schemaUri() {
        return resource.getSchema();
    }
}
