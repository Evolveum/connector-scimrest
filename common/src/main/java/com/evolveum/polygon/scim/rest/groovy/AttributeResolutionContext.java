package com.evolveum.polygon.scim.rest.groovy;

import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;

public interface AttributeResolutionContext {

    ObjectClassScripting objectClass(String name);

    default ConnectorObjectBuilder getValue() {
        return value();
    }

    ConnectorObjectBuilder value();
}
