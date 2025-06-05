package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.AttributeMapping;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObjectReference;
import org.identityconnectors.framework.common.objects.ObjectClass;

import java.util.Set;

public class ScimGroupToConnectorObjectReference implements AttributeMapping {


    private final ObjectClass referencedObjectClass;

    public ScimGroupToConnectorObjectReference(ObjectClass targetObjectClass) {
        this.referencedObjectClass = targetObjectClass;
    }

    @Override
    public Class<?> connIdType() {
        return  ConnectorObjectReference.class;
    }

    @Override
    public Class<?> primaryWireType() {
        return ObjectNode.class;
    }

    @Override
    public Set<Class<?>> supportedWireTypes() {
        return Set.of();
    }

    @Override
    public Object toWireValue(Object value) throws IllegalArgumentException {
        return null;
    }

    @Override
    public Object toConnIdValue(Object value) throws IllegalArgumentException {
        if (value instanceof ObjectNode remote) {
            var builder = new ConnectorObjectBuilder();
            builder.setObjectClass(referencedObjectClass);
            builder.setUid(remote.get("value").asText());
            builder.setName(remote.get("display").asText());
            return new ConnectorObjectReference(builder.build());
        }
        throw new IllegalArgumentException();
    }
}
