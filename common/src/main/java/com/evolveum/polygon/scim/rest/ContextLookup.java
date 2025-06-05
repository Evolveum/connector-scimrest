package com.evolveum.polygon.scim.rest;

public interface ContextLookup {

    <T extends RetrievableContext> T get(Class<T> contextType) throws IllegalStateException;

}
