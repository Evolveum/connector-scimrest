package com.evolveum.polygon.scim.rest.schema;

import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class RestObjectClass {

    ObjectClass clazz;
    ObjectClassInfo classInfo;

    Map<String, RestAttribute> attributes = new HashMap<>();

    public ConnectorObjectBuilder newObjectBuilder() {
        ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
        builder.setObjectClass(clazz);
        return builder;
    }

    public Collection<RestAttribute> attributes() {
        return attributes.values();
    }

}
