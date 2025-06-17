package com.evolveum.polygon.scim.rest.schema;

import com.evolveum.polygon.scim.rest.ValueMapping;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

public class ScimAttributeMapping extends JsonAttributeMapping {

    public ScimAttributeMapping(String name, ValueMapping<Object, JsonNode> mapping) {
        super(name, mapping);
    }

    @Override
    public JsonNode attributeFromObject(ObjectNode object) {
        if (name != null) {
            return object.get(name);
        }
        // FIXME: Later here will be SCIM path navigation support (if attribute is defined by SCIM path)
        return null;
    }

}
