package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.ContextLookup;
import com.evolveum.polygon.scim.rest.JsonValueMapping;
import com.evolveum.polygon.scim.rest.ScimContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObjectReference;
import org.identityconnectors.framework.common.objects.ObjectClass;

import java.util.Set;

public class ScimMemberToConnectorObjectReference implements JsonValueMapping {

    private final ContextLookup contextLookup;

    public ScimMemberToConnectorObjectReference(ContextLookup contextLookup) {
        this.contextLookup = contextLookup;
    }

    @Override
    public Class<ConnectorObjectReference> connIdType() {
        return  ConnectorObjectReference.class;
    }

    @Override
    public Class<? extends JsonNode> primaryWireType() {
        return ObjectNode.class;
    }

    @Override
    public Set<Class<? extends JsonNode>> supportedWireTypes() {
        return Set.of(ObjectNode.class);
    }

    @Override
    public JsonNode toWireValue(Object value) throws IllegalArgumentException {
        return null;
    }

    @Override
    public ConnectorObjectReference toConnIdValue(JsonNode value) throws IllegalArgumentException {
        if (value instanceof ObjectNode remote) {
            var builder = new ConnectorObjectBuilder();
            builder.setObjectClass(contextLookup.get(ScimContext.class).objectClassFromUri(remote.get("$ref").asText()));

            builder.setUid(remote.get("value").asText());
            builder.setName(remote.get("display").asText());
            return new ConnectorObjectReference(builder.build());
        }
        throw new IllegalArgumentException();
    }
}
