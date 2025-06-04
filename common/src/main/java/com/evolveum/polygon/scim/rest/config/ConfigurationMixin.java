package com.evolveum.polygon.scim.rest.config;

/**
 * Marker interface for staticly defined configuration partials.
 *
 * This partials represents common configuration properties for various aspects
 * of the connector.
 */
public interface ConfigurationMixin {


    /**
     * Returns extended configuration such as {@link RestClientConfiguration.BasicAuthorization} or {@link RestClientConfiguration.TokenAuthorization}.
     *
     * Default implementation returns the current instance casted to extended configuration
     * otherwise throws an {@link IllegalStateException}.
     *
     * @param <E> the type of the extended configuration
     * @param extendedConfiguration the class of the extended configuration type to cast to
     * @return an instance of the specified extended configuration type
     * @throws IllegalStateException if the current instance is not of the specified type
     */
    default  <E extends ConfigurationMixin> E require(Class<E> extendedConfiguration) {
        if (extendedConfiguration.isInstance(this)) {
            return extendedConfiguration.cast(this);
        }
        throw new IllegalStateException("Configuration of type " + extendedConfiguration.getSimpleName() + " required");
    }
}
