package com.evolveum.polygon.scimrest.groovy;

import com.evolveum.polygon.scimrest.groovy.api.HttpMethod;
import com.evolveum.polygon.scimrest.groovy.api.RestDeleteOperationBuilder;
import com.evolveum.polygon.scimrest.spi.DeleteOperation;

public class RestDeleteOperationBuilderImpl implements RestObjectOperationBuilder<DeleteOperation>, RestDeleteOperationBuilder {

    public RestDeleteOperationBuilderImpl(BaseOperationSupportBuilder parent) {

    }


    @Override
    public Endpoint endpoint(HttpMethod method, String path) {
        return null;
    }

    @Override
    public DeleteOperation build() {
        return null;
    }

}
