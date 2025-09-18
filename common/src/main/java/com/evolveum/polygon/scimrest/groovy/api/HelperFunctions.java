package com.evolveum.polygon.scimrest.groovy.api;

import com.fasterxml.jackson.databind.node.ValueNode;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObjectReference;
import org.identityconnectors.framework.common.objects.ObjectClass;

public interface HelperFunctions {

    default String stripPrefix(String prefix, ValueNode node) {
        if (node == null) {
            return null;
        }
        return stripPrefix(prefix, node.asText());
    }

    default String stripPrefix(String prefix, String value) {
        if (value != null && value.startsWith(prefix)) {
            return value.substring(prefix.length());
        }
        return value;
    }

    default ConnectorObjectReference connectorObjectReference(String objectClass, String uid, String name) {
        var obj = new ConnectorObjectBuilder()
                .setObjectClass(new ObjectClass(objectClass))
                .setUid(uid)
                .setName(name);
        return new ConnectorObjectReference(obj.build());
    }

    default ConnectorObjectReference connectorObjectReference(ConnectorObject object) {
        return new ConnectorObjectReference(object);
    }
}
