package com.evolveum.polygon.scimrest.groovy.api;

import com.evolveum.polygon.scimrest.groovy.GroovyClosures;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public interface RestOperationBuilder<E extends EndpointBuilder> extends GroovyHttpOperationMixin {

    E endpoint(String path);

    E endpoint(HttpMethod method, String path);

    default E endpoint(String path, @DelegatesTo(value = EndpointBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> value) {
        return GroovyClosures.callAndReturnDelegate(value, endpoint(path));
    }

    default E endpoint(HttpMethod method, String path, @DelegatesTo(value = EndpointBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> value) {
        return GroovyClosures.callAndReturnDelegate(value, endpoint(method, path));
    }



}
