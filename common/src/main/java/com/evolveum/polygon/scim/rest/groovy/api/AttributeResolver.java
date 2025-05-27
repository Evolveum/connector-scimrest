package com.evolveum.polygon.scim.rest.groovy.api;

import com.evolveum.polygon.scim.rest.schema.RestAttribute;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;

import java.util.Set;

public interface AttributeResolver {

    Set<RestAttribute> getSupportedAttributes();

    void resolveSingle(ConnectorObjectBuilder builder);

    default void resolve(Iterable<ConnectorObjectBuilder> builders) {
        for (ConnectorObjectBuilder builder : builders) {
            resolveSingle(builder);
        }
    }
}
