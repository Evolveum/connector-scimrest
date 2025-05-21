package com.evolveum.polygon.scim.rest.groovy;

public interface FilterAwareSearchProcessorBuilder<HC> {

    boolean emptyFilterSupported();

    FilterAwareExecuteQueryProcessor<HC> build();
}
