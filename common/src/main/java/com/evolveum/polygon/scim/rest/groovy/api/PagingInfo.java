package com.evolveum.polygon.scim.rest.groovy.api;

public record PagingInfo(int pageSize, int pageOffset) {

    public int getPageOffset() {
        return pageOffset;
    }

    public int getPageSize() {
        return pageSize;
    }
}
