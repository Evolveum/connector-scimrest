package com.evolveum.polygon.scim.rest.groovy.api;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

/**
 * Interface for building attribute resolvers.
 *
 * Attribute resolvers are used to resolve attributes from a data source into ConnID object.
 *
 * This interface provides methods to configure which attributes are supported by resolver,
 * the method of resolution and implementation of resolution.
 *
 * @param <HC> The type of the context in which the attribute resolver operates.
 */
public interface AttributeResolverBuilder {


    ResolutionType PER_OBJECT = ResolutionType.PER_OBJECT;
    ResolutionType BATCH = ResolutionType.BATCH;


    /**
     * Sets the resolution type for handling attributes.
     *
     * {@link #PER_OBJECT} handling provides only one object to resolver at a time, for which
     * supported attributes should be resolved. This type of handling should be used only if implementation / remote API
     * does not support retrieving additional data for multiple objects.
     *
     * {@link #BATCH} handling is preferred if possible (remote API allows fetching additional attributes for multiple objects).
     *
     * @param type The resolution type to be used.
     * @return This builder instance for method chaining.
     */
    AttributeResolverBuilder resolutionType(ResolutionType type);

    /**
     * Groovy implementation of attribute resolution.
     *
     * The provided closure will be executed in a delegate-first context, with an instance of {@link AttributeResolutionContext}
     * representing the context of attribute resolution.
     *
     * @param closure The closure defining custom behavior for attribute resolution.
     * @return This builder instance for method chaining.
     */
    AttributeResolverBuilder implementation(@DelegatesTo(value = AttributeResolutionContext.class, strategy = Closure.DELEGATE_FIRST) Closure<?> closure);

    enum ResolutionType {
        /**
         * Each object will be handled individually, regardless of the total number of objects involved.
         */
        PER_OBJECT,
        /**
         * Objects will be handled in batches, use this if backing implementation supports handling multiple objects per request.
         */
        BATCH
    }

    /**
     * Marks attribute as supported for resolver.
     *
     * The final resolver will be used to fetch and resolve contents of specified attributes.
     *
     * @param attributeName name of attribute
     * @return this builder
     */
    AttributeResolverBuilder attribute(String attributeName);

}
