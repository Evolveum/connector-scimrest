package com.evolveum.polygon.scim.rest.groovy.api;

import com.evolveum.polygon.scim.rest.schema.RestAttribute;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;

import java.util.Set;

/**
 * Interface for resolving additional attributes in a connector object.
 *
 * Attribute resolvers are used to resolve attributes from a data source into ConnID object.
 *
 */
public interface AttributeResolver {

    Set<RestAttribute> getSupportedAttributes();

    /**
     * Resolves additional attributes for single object.
     *
     * This method is part of an attribute resolver implementation, used to fetch and set additional
     * attributes in a connector object. The `ConnectorObjectBuilder` passed to this method should
     * be populated with any additional data required by the resolver.
     *
     * @param builder The builder for the connector object whose attributes need to be resolved.
     */
    void resolveSingle(ConnectorObjectBuilder builder);

    /**
     * Resolves additional attributes for multiple connector objects.
     *
     * Override this method if attribute resolution could be optimized for multiple objects - eg. fetching objects in
     * batch and resolving their attributes together.
     *
     * @param builders An iterable collection of `ConnectorObjectBuilder` objects whose attributes need to be resolved.
     */
    default void resolve(Iterable<ConnectorObjectBuilder> builders) {
        for (ConnectorObjectBuilder builder : builders) {
            resolveSingle(builder);
        }
    }
}
