package com.evolveum.polygon.scim.rest.groovy;

public interface FilterAwareSearchProcessorBuilder {

    boolean isEnabled();

    boolean emptyFilterSupported();

    default boolean anyFilterSupported() {
        return false;
    }

    FilterAwareExecuteQueryProcessor build();
}
