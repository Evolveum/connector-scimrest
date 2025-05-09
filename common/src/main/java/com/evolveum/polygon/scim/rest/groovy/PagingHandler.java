package com.evolveum.polygon.scim.rest.groovy;


import com.evolveum.polygon.scim.rest.RestContext;

public interface PagingHandler {

    void handlePaging(RestContext.RequestBuilder builder, PagingInfo pagingInfo);

}
