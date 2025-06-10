package com.evolveum.polygon.scim.rest.groovy.api;

import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;

public interface AttributeResolutionContext extends BaseScriptContext {

    default ConnectorObjectBuilder getValue() {
        return value();
    }

    ConnectorObjectBuilder value();

}
