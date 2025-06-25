/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy.api;

import com.evolveum.polygon.scimrest.ContextLookup;
import com.evolveum.polygon.scimrest.schema.MappedAttribute;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;

import java.util.Set;

/**
 * Interface for resolving additional attributes in a connector object.
 *
 * Attribute resolvers are used to resolve attributes from a data source into ConnID object.
 *
 */
public interface AttributeResolver {

    String SKIP_ATTRIBUTE_RESOLUTION_OPTION = "skipAttributeResolution";

    Set<MappedAttribute> getSupportedAttributes();

    /**
     * Resolves additional attributes for single object.
     *
     * This method is part of an attribute resolver implementation, used to fetch and set additional
     * attributes in a connector object. The `ConnectorObjectBuilder` passed to this method should
     * be populated with any additional data required by the resolver.
     *
     * @param builder The builder for the connector object whose attributes need to be resolved.
     */
    void resolveSingle(ContextLookup context, ConnectorObjectBuilder builder);

    /**
     * Resolves additional attributes for multiple connector objects.
     *
     * Override this method if attribute resolution could be optimized for multiple objects - eg. fetching objects in
     * batch and resolving their attributes together.
     *
     * @param builders An iterable collection of `ConnectorObjectBuilder` objects whose attributes need to be resolved.
     */
    default void resolve(ContextLookup context,Iterable<ConnectorObjectBuilder> builders) {
        for (ConnectorObjectBuilder builder : builders) {
            resolveSingle(context, builder);
        }
    }

    AttributeResolverBuilder.ResolutionType resolutionType();


    default OperationOptions skipAttributeResolution() {
        return new OperationOptionsBuilder()
                .setOption(SKIP_ATTRIBUTE_RESOLUTION_OPTION, true).build();
    }

}
