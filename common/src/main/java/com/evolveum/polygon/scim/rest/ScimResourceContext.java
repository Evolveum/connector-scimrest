package com.evolveum.polygon.scim.rest;

import com.evolveum.polygon.scim.rest.api.AttributePath;
import com.unboundid.scim2.common.types.AttributeDefinition;
import com.unboundid.scim2.common.types.ResourceTypeResource;

import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;

public record ScimResourceContext(ResourceTypeResource resource, String relativeEndpoint, com.unboundid.scim2.common.types.SchemaResource primarySchema,
                                  HashMap<String, com.unboundid.scim2.common.types.SchemaResource> extensions)  {

    public URI schemaUri() {
        return resource.getSchema();
    }

    public AttributeDefinition findAttributeDefinition(AttributePath path) {

        var components = path.components().iterator();
        if (!components.hasNext()) {
            return null;
        }
        var firstComponent = components.next();
        AttributeDefinition ret = null;
        var schema = primarySchema;
        if (firstComponent instanceof AttributePath.Extension extension) {
            schema = extensions.get(extension.name());
            firstComponent = nextAttribute(components);
        }
        if (firstComponent instanceof AttributePath.Attribute attribute) {
            ret = findAttribute(attribute.name(),schema.getAttributes());
        }
        while (ret != null && components.hasNext()) {
            var attribute = nextAttribute(components);
            ret = findAttribute(attribute.name(),ret.getSubAttributes());
        }
        return ret;
    }

    private AttributePath.Attribute nextAttribute(Iterator<AttributePath.Component> components) {
        while (components.hasNext()) {
            var ret = components.next();
            if (ret instanceof AttributePath.Attribute attribute) {
                return attribute;
            }
            // Skipping filters
        }
        return null;

    }

    private AttributeDefinition findAttribute(String name, Collection<AttributeDefinition> attributes) {
        for (AttributeDefinition attr : attributes) {
            if (attr.getName().equals(name)) {
                return attr;
            }
        }
        return null;
    }


}
