package com.evolveum.polygon.scim.rest.api;

public interface Resolver<C,P> {

    P resolve(C contextNode);

}
