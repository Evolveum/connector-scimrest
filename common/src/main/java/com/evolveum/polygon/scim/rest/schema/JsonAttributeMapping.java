package com.evolveum.polygon.scim.rest.schema;

import com.evolveum.polygon.scim.rest.JsonValueMapping;
import com.evolveum.polygon.scim.rest.ValueMapping;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record JsonAttributeMapping(String name,
        ValueMapping<Object, JsonNode> valueMapping) implements AttributeProtocolMapping<JsonNode> {

    @Override
    public Class<?> connIdType() {
        return valueMapping.connIdType();
    }

    @Override
    public Object extractValue(JsonNode remoteObj) {
        return valueMapping.toConnIdValue(remoteObj);
    }

    @Override
    public List<Object> extractMultipleValues(JsonNode wireValues) {
        return valueMapping.toConnIdValues(wireValues);
    }
}
