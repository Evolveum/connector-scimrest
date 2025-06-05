package com.evolveum.polygon.scim.rest.schema;

public interface AttributeProtocolMappingBuilder {

    Class<?> suggestedConnIdType();

    AttributeProtocolMapping<?> build();

}
