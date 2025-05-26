package com.evolveum.polygon.scim.rest.groovy.api;

import java.net.http.HttpResponse;

public record ResponseWrapper<BF>(HttpResponse<BF> response) {

    public HttpResponse<BF> getResponse() {
        return response;
    }
}
