package com.evolveum.polygon.scim.rest;

import com.unboundid.scim2.client.ScimService;
import com.unboundid.scim2.common.types.ResourceTypeResource;
import com.unboundid.scim2.common.types.SchemaResource;
import com.unboundid.scim2.common.types.ServiceProviderConfigResource;
import org.identityconnectors.framework.common.exceptions.ConnectorException;

import java.util.HashMap;
import java.util.Map;

public class ScimContext {

    private final ScimService client;
    private ServiceProviderConfigResource providerConfig;

    private Map<String, ScimResourceContext> resources;

    private Map<String, SchemaResource> schemas = new HashMap<>();

    public ScimContext(ScimService scimService) {
        this.client = scimService;
    }

    public void initialize() {
        try {
            this.providerConfig = client.getServiceProviderConfig();

            for (var schema : client.getSchemas()) {
                schemas.put(schema.getId(), schema);
            }

            for (var resource : client.getResourceTypes()) {
                // FIXME: Maybe we should wrap it into nice context object
                // holding schemas together, other resources

                var primary = schemas.get(resource.getSchema().toString());
                var extensions = new HashMap<String, SchemaResource>();
                for (var ext : resource.getSchemaExtensions()) {
                    extensions.put(ext.getSchema().toString(), schemas.get(ext.getSchema().toString()));
                }
                resources.put(resource.getId(), new ScimResourceContext(resource, primary, extensions));
            }

        } catch (Exception e) {
            // FIXME: Throw correct connector exceptions
            throw new ConnectorException(e);
        }

    }
}
