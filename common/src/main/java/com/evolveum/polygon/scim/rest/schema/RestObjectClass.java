package com.evolveum.polygon.scim.rest.schema;

import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class RestObjectClass {

    private final Map<String, RestAttribute> nativeAttributes;
    private final Map<String, RestAttribute> connIdAttributes;
    ObjectClass clazz;
    ObjectClassInfo connId;

    Map<String, RestAttribute> attributes = new HashMap<>();

    public RestObjectClass(ObjectClassInfo connId, Map<String, RestAttribute> nativeAttrs, Map<String, RestAttribute> connIdAttrs) {
        this.connId = connId;
        this.clazz = new ObjectClass(connId.getType());
        this.nativeAttributes = nativeAttrs;
        this.connIdAttributes = connIdAttrs;
    }

    public ConnectorObjectBuilder newObjectBuilder() {
        ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
        builder.setObjectClass(clazz);
        return builder;
    }

    public Collection<RestAttribute> attributes() {
        return nativeAttributes.values();
    }

    public ObjectClassInfo connId() {
        return connId;
    }

    public ObjectClass objectClass() {
        return clazz;
    }

    public RestAttribute attributeFromProtocolName(String protocolName) {
        return nativeAttributes.get(protocolName);
    }
}
