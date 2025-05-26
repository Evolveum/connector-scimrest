package com.evolveum.polygon.scim.rest.groovy;


import com.evolveum.polygon.scim.rest.RestContext;
import com.evolveum.polygon.scim.rest.groovy.api.PagingInfo;

public interface PagingHandler {

    void handlePaging(RestContext.RequestBuilder builder, PagingInfo pagingInfo);

}
